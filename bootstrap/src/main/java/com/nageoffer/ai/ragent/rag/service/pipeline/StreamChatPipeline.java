/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.core.grounding.GroundingEvaluationService;
import com.nageoffer.ai.ragent.rag.core.clarification.MissingParameterClosedLoopService;
import com.nageoffer.ai.ragent.rag.core.citation.CitationBufferedCallback;
import com.nageoffer.ai.ragent.rag.core.citation.CitationFinalStatus;
import com.nageoffer.ai.ragent.rag.core.citation.CitationValidationResult;
import com.nageoffer.ai.ragent.rag.core.citation.CitationValidator;
import com.nageoffer.ai.ragent.rag.core.entity.EntitySelectionClosedLoopService;
import com.nageoffer.ai.ragent.rag.core.dependency.DependencyExecutionCoordinator;
import com.nageoffer.ai.ragent.rag.core.guidance.IntentGuidanceService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolutionResult;
import com.nageoffer.ai.ragent.rag.core.routing.AllowedRoute;
import com.nageoffer.ai.ragent.rag.core.routing.PreRoutingPolicy;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingDecision;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingDecisionResult;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingGovernanceService;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptContext;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.core.prompt.RAGPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import com.nageoffer.ai.ragent.rag.trace.ExecutionTraceRecorder;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

/**
 * 流式对话流水线
 * <p>
 * 承载从 RAGChatServiceImpl 提取的业务编排逻辑：
 * 记忆加载 -> 改写拆分 -> 意图解析 -> 歧义引导 -> 系统响应 / 检索 -> Prompt 组装 -> 流式输出
 * <p>
 * 流水线模式：通过私有方法 + boolean 返回值（handleXxx 返回 true 表示已处理并短路）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatPipeline {

    private final SearchChannelProperties searchProperties;
    private final ConversationMemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final LLMService llmService;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final StreamTaskManager taskManager;
    private final ExecutionTraceRecorder executionTraceRecorder;
    private final MissingParameterClosedLoopService missingParameterClosedLoopService;
    private final EntitySelectionClosedLoopService entitySelectionClosedLoopService;
    private final DependencyExecutionCoordinator dependencyExecutionCoordinator;
    private final PreRoutingPolicy preRoutingPolicy;
    private final RoutingGovernanceService routingGovernanceService;
    private final CitationValidator citationValidator;
    private final GroundingEvaluationService groundingEvaluationService;

    /**
     * 执行流式对话管道
     */
    public void execute(StreamChatContext ctx) {
        loadMemory(ctx);
        if (handlePendingDependency(ctx) || handlePendingSelection(ctx) || handlePendingClarification(ctx)) {
            return;
        }
        if (handlePreRouting(ctx)) {
            return;
        }
        rewriteQuery(ctx);
        resolveIntents(ctx);
        if (handleRoutingGovernance(ctx)) {
            return;
        }

        if (handleGuidance(ctx)) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return;
        }
        if (handleSystemOnly(ctx)) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return;
        }

        RetrievalContext retrievalCtx = retrieve(ctx);
        if (handleDirectResponse(ctx)) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return;
        }
        if (handleEmptyRetrieval(ctx, retrievalCtx)) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return;
        }

        streamRagResponse(ctx, retrievalCtx);
    }

    // ==================== 流水线阶段 ====================

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    private void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
        ctx.setRewriteResult(rewriteResult);
    }

    private void resolveIntents(StreamChatContext ctx) {
        IntentResolutionResult resolution = intentResolver.resolveWithStatus(ctx.getRewriteResult());
        ctx.setIntentResolution(resolution);
        ctx.setSubIntents(resolution.intents());
    }

    private boolean handlePreRouting(StreamChatContext ctx) {
        var result = preRoutingPolicy.decide(ctx.getQuestion(), ctx.getUserId());
        if (result.isEmpty()) return false;
        applyDecision(ctx, result.get());
        respondFixed(ctx, result.get().getFixedResponse());
        return true;
    }

    private boolean handleRoutingGovernance(StreamChatContext ctx) {
        RoutingGovernanceService.GovernanceOutcome outcome = routingGovernanceService.decide(
                ctx.getQuestion(), ctx.getRewriteResult().rewrittenQuestion(), ctx.getIntentResolution());
        RoutingDecisionResult decision = outcome.decision();
        ctx.setRoutingDecision(decision);
        ctx.setSubIntents(outcome.authorizedIntents());
        applyDecision(ctx, decision);
        recordAllowedRoutes(ctx, decision.getAllowedRoutes());
        if (decision.getDecision() == RoutingDecision.ROUTED
                || decision.getDecision() == RoutingDecision.GLOBAL_KB_FALLBACK) {
            return false;
        }
        respondFixed(ctx, decision.getFixedResponse());
        return true;
    }

    private void applyDecision(StreamChatContext ctx, RoutingDecisionResult decision) {
        ctx.setRoutingDecision(decision);
        ctx.getExecutionRecord().setRoutingDecision(decision.getDecision());
        ctx.getExecutionRecord().setRoutingReason(decision.getReason());
    }

    private void recordAllowedRoutes(StreamChatContext ctx, List<AllowedRoute> routes) {
        for (AllowedRoute route : routes) {
            Double confidence = ctx.getSubIntents().stream()
                    .filter(si -> route.subQuestion().equals(si.subQuestion()))
                    .flatMap(si -> si.nodeScores().stream())
                    .filter(ns -> route.intentId().equals(ns.getNode().getId()))
                    .map(ns -> ns.getScore()).findFirst().orElse(null);
            ctx.getExecutionRecord().addRoute(ExecutionRecord.RouteRecord.builder()
                    .subQuestion(route.subQuestion()).intent(route.intentId()).confidence(confidence)
                    .resource(ExecutionRecord.ResourceRecord.builder()
                            .type(route.kind() == null ? "KB" : route.kind().name())
                            .name(route.resourceName()).build()).build());
        }
    }

    private void respondFixed(StreamChatContext ctx, String response) {
        markCitationNotApplicable(ctx.getExecutionRecord());
        ctx.getCallback().onContent(StrUtil.blankToDefault(response, "当前请求暂时无法处理。"));
        ctx.getCallback().onComplete();
        executionTraceRecorder.record(ctx.getExecutionRecord());
    }

    private boolean handlePendingDependency(StreamChatContext ctx) {
        var outcome = dependencyExecutionCoordinator.resume(
                ctx.getUserId(), ctx.getConversationId(), ctx.getQuestion(), ctx.getExecutionRecord());
        if (outcome.isEmpty()) return false;
        var value = outcome.get();
        if (value.isDirect()) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            ctx.getCallback().onContent(value.directResponse());
            ctx.getCallback().onComplete();
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return true;
        }
        streamRagResponse(ctx, value.retrievalContext());
        return true;
    }

    private boolean handlePendingSelection(StreamChatContext ctx) {
        var outcome = entitySelectionClosedLoopService.resume(
                ctx.getUserId(), ctx.getConversationId(), ctx.getQuestion(), ctx.getExecutionRecord());
        if (outcome.isEmpty()) return false;
        var value = outcome.get();
        if (value.isDirect()) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            ctx.getCallback().onContent(value.directResponse());
            ctx.getCallback().onComplete();
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return true;
        }
        ctx.setRewriteResult(new RewriteResult(value.originalQuery(), List.of(value.originalQuery())));
        ctx.setSubIntents(value.subIntents());
        streamRagResponse(ctx, value.retrievalContext());
        return true;
    }

    private boolean handlePendingClarification(StreamChatContext ctx) {
        var outcome = missingParameterClosedLoopService.resume(
                ctx.getUserId(), ctx.getConversationId(), ctx.getQuestion(), ctx.getExecutionRecord());
        if (outcome.isEmpty()) return false;
        var value = outcome.get();
        if (value.isDirect()) {
            markCitationNotApplicable(ctx.getExecutionRecord());
            ctx.getCallback().onContent(value.directResponse());
            ctx.getCallback().onComplete();
            executionTraceRecorder.record(ctx.getExecutionRecord());
            return true;
        }
        ctx.setRewriteResult(new RewriteResult(value.originalQuery(), List.of(value.originalQuery())));
        ctx.setSubIntents(value.subIntents());
        streamRagResponse(ctx, value.retrievalContext());
        return true;
    }

    private boolean handleDirectResponse(StreamChatContext ctx) {
        String response = ctx.getExecutionRecord().getDirectResponse();
        if (StrUtil.isBlank(response)) return false;
        ctx.getCallback().onContent(response);
        ctx.getCallback().onComplete();
        return true;
    }

    private boolean handleGuidance(StreamChatContext ctx) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getSubIntents()
        );
        if (!decision.isPrompt()) {
            return false;
        }
        StreamCallback callback = ctx.getCallback();
        callback.onContent(decision.getPrompt());
        callback.onComplete();
        return true;
    }

    private boolean handleSystemOnly(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = ctx.getSubIntents();
        boolean allSystemOnly = subIntents.stream()
                .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
        if (!allSystemOnly) {
            return false;
        }
        String customPrompt = subIntents.stream()
                .flatMap(si -> si.nodeScores().stream())
                .map(ns -> ns.getNode().getPromptTemplate())
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        StreamCancellationHandle handle = streamSystemResponse(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getHistory(),
                customPrompt,
                ctx.getCallback()
        );
        taskManager.bindHandle(ctx.getTaskId(), handle);
        return true;
    }

    private RetrievalContext retrieve(StreamChatContext ctx) {
        return retrievalEngine.retrieve(
                ctx.getSubIntents(), searchProperties.getDefaultTopK(), ctx.getExecutionRecord(),
                ctx.getUserId(), ctx.getConversationId(), ctx.getQuestion(), ctx.getRoutingDecision());
    }

    private boolean handleEmptyRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        if (!retrievalCtx.isEmpty()) {
            return false;
        }
        StreamCallback callback = ctx.getCallback();
        callback.onContent("未检索到与问题相关的文档内容。");
        callback.onComplete();
        return true;
    }

    private void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        // 聚合所有意图用于 prompt 规划
        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());

        StreamCallback answerCallback = ctx.getCallback();
        if (retrievalCtx.hasKb()) {
            boolean citationRequired = retrievalCtx.getEvidenceRecords().stream()
                    .anyMatch(item -> item.isSelectedForPrompt() && StrUtil.isNotBlank(item.getEvidenceId()));
            answerCallback = new CitationBufferedCallback(
                    ctx.getCallback(), citationValidator, retrievalCtx.getEvidenceRecords(), citationRequired,
                    result -> {
                        recordCitation(ctx.getExecutionRecord(), result);
                        recordGrounding(ctx, retrievalCtx, result.finalAnswer());
                        executionTraceRecorder.record(ctx.getExecutionRecord());
                    },
                    () -> executionTraceRecorder.record(ctx.getExecutionRecord()));
        } else if (ctx.isGroundingEvaluation()) {
            answerCallback = new CitationBufferedCallback(
                    ctx.getCallback(), citationValidator, List.of(), false,
                    result -> {
                        markCitationNotApplicable(ctx.getExecutionRecord());
                        recordGrounding(ctx, retrievalCtx, result.finalAnswer());
                        executionTraceRecorder.record(ctx.getExecutionRecord());
                    },
                    () -> executionTraceRecorder.record(ctx.getExecutionRecord()));
        } else {
            markCitationNotApplicable(ctx.getExecutionRecord());
            executionTraceRecorder.record(ctx.getExecutionRecord());
        }

        StreamCancellationHandle handle = streamLLMResponse(
                ctx.getRewriteResult(),
                retrievalCtx,
                mergedGroup,
                ctx.getHistory(),
                ctx.isDeepThinking(),
                answerCallback
        );
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }

    private void recordCitation(ExecutionRecord record, CitationValidationResult result) {
        record.setCitation(ExecutionRecord.CitationTraceRecord.builder()
                .usedEvidenceIds(result.usedEvidenceIds())
                .validEvidenceIds(result.validEvidenceIds())
                .invalidEvidenceIds(result.invalidEvidenceIds())
                .initialStatus(result.initialStatus().name())
                .finalStatus(result.finalStatus().name())
                .cleanupApplied(result.cleanupApplied())
                .build());
        log.info("Citation validation completed: initialStatus={}, finalStatus={}, used={}, valid={}, invalid={}, cleanupApplied={}",
                result.initialStatus(), result.finalStatus(), result.usedEvidenceIds(), result.validEvidenceIds(),
                result.invalidEvidenceIds(), result.cleanupApplied());
    }

    private void recordGrounding(StreamChatContext ctx, RetrievalContext retrievalCtx, String finalAnswer) {
        ctx.getExecutionRecord().setGrounding(ctx.isGroundingEvaluation()
                ? groundingEvaluationService.evaluate(finalAnswer, retrievalCtx, ctx.getExecutionRecord())
                : groundingEvaluationService.notApplicable());
    }

    private void markCitationNotApplicable(ExecutionRecord record) {
        if (record.getCitation() == null) {
            record.setCitation(ExecutionRecord.CitationTraceRecord.builder()
                    .finalStatus(CitationFinalStatus.NOT_APPLICABLE.name())
                    .build());
        }
        if (record.getGrounding() == null) {
            record.setGrounding(groundingEvaluationService.notApplicable());
        }
    }

    // ==================== LLM 响应 ====================

    private StreamCancellationHandle streamSystemResponse(String question, List<ChatMessage> history,
                                                          String customPrompt, StreamCallback callback) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(question));

        ChatRequest req = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();
        return llmService.streamChat(req, callback);
    }

    private StreamCancellationHandle streamLLMResponse(RewriteResult rewriteResult, RetrievalContext ctx,
                                                       IntentGroup intentGroup, List<ChatMessage> history,
                                                       boolean deepThinking, StreamCallback callback) {
        PromptContext promptContext = PromptContext.builder()
                .question(rewriteResult.rewrittenQuestion())
                .mcpContext(ctx.getMcpContext())
                .kbContext(ctx.getKbContext())
                .mcpIntents(intentGroup.mcpIntents())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(ctx.getIntentChunks())
                .evidenceRecords(ctx.getEvidenceRecords())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                history,
                rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions()  // 传入子问题列表
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(deepThinking)
                .temperature(ctx.hasMcp() ? 0.3D : 0D)  // MCP 场景稍微放宽温度
                .topP(ctx.hasMcp() ? 0.8D : 1D)
                .build();

        return llmService.streamChat(chatRequest, callback);
    }
}
