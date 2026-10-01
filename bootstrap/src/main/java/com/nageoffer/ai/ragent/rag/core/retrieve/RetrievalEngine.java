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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.trace.RagTraceNode;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.clarification.MissingParameterClosedLoopService;
import com.nageoffer.ai.ragent.rag.core.clarification.MissingParameterGate;
import com.nageoffer.ai.ragent.rag.core.clarification.ExactBusinessParameterFilter;
import com.nageoffer.ai.ragent.rag.core.clarification.ParameterCheckResult;
import com.nageoffer.ai.ragent.rag.core.clarification.ToolParameterContract;
import com.nageoffer.ai.ragent.rag.core.clarification.ToolParameterContractRegistry;
import com.nageoffer.ai.ragent.rag.core.entity.EntityResolutionRequest;
import com.nageoffer.ai.ragent.rag.core.entity.EntityResolutionResult;
import com.nageoffer.ai.ragent.rag.core.entity.EntityResolutionStatus;
import com.nageoffer.ai.ragent.rag.core.entity.EntityResolver;
import com.nageoffer.ai.ragent.rag.core.entity.EntitySelectionClosedLoopService;
import com.nageoffer.ai.ragent.rag.core.dependency.DependencyExecutionCoordinator;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceFormatter;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceIdAssigner;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceRecord;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceTraceMapper;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScoreFilters;
import com.nageoffer.ai.ragent.rag.core.mcp.McpParameterExtractor;
import com.nageoffer.ai.ragent.rag.core.mcp.HuaweiMallRuntimeArguments;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import com.nageoffer.ai.ragent.rag.core.prompt.ContextFormatter;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingDecisionResult;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CONTEXT_FORMAT_PATH;
import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.MULTI_CHANNEL_KEY;

/**
 * 检索引擎
 * 负责协调多通道检索（知识库）和 MCP（模型控制协议）工具的调用，并对检索结果进行重排序和格式化，最终生成用于 LLM 的上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalEngine {

    private final SearchChannelProperties searchProperties;
    private final ContextFormatter contextFormatter;
    private final PromptTemplateLoader templateLoader;
    private final McpParameterExtractor mcpParameterExtractor;
    private final McpToolRegistry mcpToolRegistry;
    private final HuaweiMallRuntimeArguments huaweiMallRuntimeArguments;
    private final ToolParameterContractRegistry parameterContractRegistry;
    private final MissingParameterGate missingParameterGate;
    private final ExactBusinessParameterFilter exactBusinessParameterFilter;
    private final MissingParameterClosedLoopService missingParameterClosedLoopService;
    private final EntityResolver entityResolver;
    private final EntitySelectionClosedLoopService entitySelectionClosedLoopService;
    private final DependencyExecutionCoordinator dependencyExecutionCoordinator;
    private final MultiChannelRetrievalEngine multiChannelRetrievalEngine;
    private final EvidenceIdAssigner evidenceIdAssigner;
    private final EvidenceFormatter evidenceFormatter;
    private final EvidenceTraceMapper evidenceTraceMapper;
    private final Executor ragContextExecutor;
    private final Executor mcpBatchExecutor;

    /**
     * 检索方法：根据子问题意图列表执行检索，整合知识库和MCP工具的结果
     */
    @RagTraceNode(name = "retrieval-engine", type = "RETRIEVE")
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
        return retrieve(subIntents, topK, null, null, null, null);
    }

    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK,
                                     ExecutionRecord executionRecord) {
        return retrieve(subIntents, topK, executionRecord, null, null, null);
    }

    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK,
                                     ExecutionRecord executionRecord, String userId, String conversationId) {
        return retrieve(subIntents, topK, executionRecord, userId, conversationId, null);
    }

    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK,
                                     ExecutionRecord executionRecord, String userId, String conversationId,
                                     String originalQuery) {
        return retrieve(subIntents, topK, executionRecord, userId, conversationId, originalQuery, null);
    }

    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK,
                                     ExecutionRecord executionRecord, String userId, String conversationId,
                                     String originalQuery, RoutingDecisionResult routingDecision) {
        if (CollUtil.isEmpty(subIntents)) {
            return RetrievalContext.builder()
                    .intentChunks(Map.of())
                    .build();
        }

        if (executionRecord != null && StrUtil.isNotBlank(userId) && StrUtil.isNotBlank(conversationId)) {
            String dependencyQuery = StrUtil.blankToDefault(originalQuery, subIntents.get(0).subQuestion());
            var dependency = dependencyExecutionCoordinator.execute(
                    dependencyQuery, subIntents, executionRecord, userId, conversationId);
            if (dependency.isPresent()) {
                var outcome = dependency.get();
                if (outcome.isDirect()) {
                    executionRecord.setDirectResponse(outcome.directResponse());
                    return RetrievalContext.builder().intentChunks(Map.of()).build();
                }
                return outcome.retrievalContext();
            }
        }

        int finalTopK = topK > 0 ? topK : searchProperties.getDefaultTopK();
        boolean singleMcpRequest = subIntents.size() == 1
                && NodeScoreFilters.mcp(subIntents.get(0).nodeScores()).size() == 1;
        List<CompletableFuture<SubQuestionContext>> tasks = subIntents.stream()
                .map(si -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return buildSubQuestionContext(
                                        si,
                                        resolveSubQuestionTopK(si, finalTopK),
                                        executionRecord, userId, conversationId, singleMcpRequest, routingDecision
                                );
                            } catch (Exception e) {
                                log.error("子问题上下文构建失败，降级为空上下文，question：{}", si.subQuestion(), e);
                                return new SubQuestionContext(si.subQuestion(), List.of(), "");
                            }
                        },
                        ragContextExecutor
                ))
                .toList();
        List<SubQuestionContext> contexts = tasks.stream()
                .map(CompletableFuture::join)
                .toList();

        List<EvidenceRecord> mergedEvidence = contexts.stream()
                .flatMap(context -> context.evidenceRecords().stream())
                .toList();
        evidenceIdAssigner.assign(mergedEvidence);
        if (executionRecord != null) {
            executionRecord.setRetrievedEvidence(evidenceTraceMapper.map(mergedEvidence));
        }
        Map<String, List<RetrievedChunk>> mergedIntentChunks = toIntentChunks(mergedEvidence);

        boolean singleQuestion = contexts.size() == 1;
        String kbContext;
        String mcpContext;

        if (singleQuestion) {
            SubQuestionContext only = contexts.get(0);
            kbContext = evidenceFormatter.format(only.evidenceRecords()).trim();
            mcpContext = StrUtil.emptyIfNull(only.mcpContext()).trim();
        } else {
            StringBuilder kbBuilder = new StringBuilder();
            StringBuilder mcpBuilder = new StringBuilder();
            int globalIndex = 0;
            for (SubQuestionContext context : contexts) {
                String formattedEvidence = evidenceFormatter.format(context.evidenceRecords());
                boolean hasKb = StrUtil.isNotBlank(formattedEvidence);
                boolean hasMcp = StrUtil.isNotBlank(context.mcpContext());
                if (hasKb || hasMcp) {
                    globalIndex++;
                }
                if (hasKb) {
                    appendSection(kbBuilder, "sub-question-kb-wrapper", globalIndex, context.question(), formattedEvidence);
                }
                if (hasMcp) {
                    appendSection(mcpBuilder, "sub-question-mcp-wrapper", globalIndex, context.question(), context.mcpContext());
                }
            }
            kbContext = kbBuilder.toString().trim();
            mcpContext = mcpBuilder.toString().trim();
        }

        return RetrievalContext.builder()
                .mcpContext(mcpContext)
                .kbContext(kbContext)
                .intentChunks(mergedIntentChunks)
                .evidenceRecords(mergedEvidence)
                .build();
    }

    private SubQuestionContext buildSubQuestionContext(SubQuestionIntent intent, int topK,
                                                       ExecutionRecord executionRecord,
                                                       String userId, String conversationId,
                                                       boolean enableMissingGate,
                                                       RoutingDecisionResult routingDecision) {
        List<NodeScore> kbIntents = NodeScoreFilters.kb(intent.nodeScores());
        List<NodeScore> mcpIntents = NodeScoreFilters.mcp(intent.nodeScores());

        List<EvidenceRecord> evidenceRecords = retrieveAndRerank(intent, topK, routingDecision);

        String mcpContext = CollUtil.isNotEmpty(mcpIntents)
                ? executeMcpAndMerge(intent.subQuestion(), mcpIntents, executionRecord,
                        userId, conversationId, enableMissingGate)
                : "";

        return new SubQuestionContext(intent.subQuestion(), evidenceRecords, mcpContext);
    }

    /**
     * 子问题实际 TopK 计算规则
     */
    private int resolveSubQuestionTopK(SubQuestionIntent intent, int fallbackTopK) {
        return NodeScoreFilters.kb(intent.nodeScores()).stream()
                .map(NodeScore::getNode)
                .filter(Objects::nonNull)
                .map(IntentNode::getTopK)
                .filter(Objects::nonNull)
                .filter(topK -> topK > 0)
                .max(Integer::compareTo)
                .orElse(fallbackTopK);
    }

    private void appendSection(StringBuilder builder, String section, int index, String question, String context) {
        if (!builder.isEmpty()) {
            builder.append("\n");
        }
        builder.append(templateLoader.renderSection(CONTEXT_FORMAT_PATH, section, Map.of(
                "index", String.valueOf(index),
                "question", question,
                "context", context
        )));
    }

    private String executeMcpAndMerge(String question, List<NodeScore> mcpIntents,
                                      ExecutionRecord executionRecord, String userId, String conversationId,
                                      boolean enableMissingGate) {
        if (CollUtil.isEmpty(mcpIntents)) {
            return "";
        }

        Map<String, List<CallToolResult>> toolResults = executeMcpTools(
                question, mcpIntents, executionRecord, userId, conversationId, enableMissingGate);
        if (toolResults.isEmpty()) {
            return "";
        }

        return contextFormatter.formatMcpContext(toolResults, mcpIntents);
    }

    private List<EvidenceRecord> retrieveAndRerank(SubQuestionIntent intent, int topK,
                                                    RoutingDecisionResult routingDecision) {
        return multiChannelRetrievalEngine.retrieveKnowledgeEvidence(List.of(intent), topK, routingDecision);
    }

    private Map<String, List<RetrievedChunk>> toIntentChunks(List<EvidenceRecord> evidenceRecords) {
        Map<String, List<RetrievedChunk>> grouped = new LinkedHashMap<>();
        evidenceRecords.stream().filter(EvidenceRecord::isSelectedForPrompt).forEach(item -> {
            String key = StrUtil.blankToDefault(item.getIntentId(), MULTI_CHANNEL_KEY);
            Float score = item.getRerankScore() == null
                    ? (item.getRetrievalScore() == null ? null : item.getRetrievalScore().floatValue())
                    : item.getRerankScore().floatValue();
            grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(
                    new RetrievedChunk(item.getChunkId(), item.getContent(), score,
                            item.getDocumentId(), item.getCollectionName(), item.getChunkIndex()));
        });
        return grouped;
    }

    /**
     * 执行 MCP 工具调用，返回按 toolId 分组的结果。
     * 每个并发任务返回不可变观察结果，join 后按意图顺序写入请求级记录。
     */
    private Map<String, List<CallToolResult>> executeMcpTools(String question,
                                                              List<NodeScore> mcpIntentScores,
                                                              ExecutionRecord executionRecord,
                                                              String userId, String conversationId,
                                                              boolean enableMissingGate) {
        if (CollUtil.isEmpty(mcpIntentScores)) {
            return Map.of();
        }

        List<CompletableFuture<ToolOutput>> futures = mcpIntentScores.stream()
                .map(ns -> CompletableFuture.supplyAsync(
                        () -> {
                            String toolId = ns.getNode().getMcpToolId();
                            try {
                                return executeSingleMcpTool(question, ns.getNode(), executionRecord, userId, conversationId, enableMissingGate);
                            } catch (Exception e) {
                                log.error("MCP 工具调用异常, toolId: {}", toolId, e);
                                CallToolResult result = CallToolResult.builder()
                                        .content(List.of(new TextContent("工具调用异常: " + e.getMessage())))
                                        .isError(true)
                                        .build();
                                return new ToolOutput(toolId, result, null,
                                        execution(toolId, Map.of(), ExecutionRecord.ToolStatus.ERROR, "EXCEPTION"));
                            }
                        },
                        mcpBatchExecutor
                ))
                .toList();

        List<ToolOutput> outputs = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
        if (executionRecord != null) {
            for (ToolOutput output : outputs) {
                executionRecord.addToolObservation(output.resolution(), output.execution());
            }
        }
        return outputs.stream()
                .filter(output -> output.result() != null)
                .collect(Collectors.groupingBy(
                        ToolOutput::toolId,
                        LinkedHashMap::new,
                        Collectors.mapping(ToolOutput::result, Collectors.toList())
                ));
    }

    private ToolOutput executeSingleMcpTool(String question, IntentNode intentNode,
                                            ExecutionRecord executionRecord, String userId,
                                            String conversationId, boolean enableMissingGate) {
        String toolId = intentNode.getMcpToolId();
        Optional<McpToolExecutor> executorOpt = mcpToolRegistry.getExecutor(toolId);
        if (executorOpt.isEmpty()) {
            log.warn("MCP 工具不存在: {}", toolId);
            return new ToolOutput(toolId, null, null,
                    execution(toolId, Map.of(), ExecutionRecord.ToolStatus.NOT_EXECUTED, "TOOL_NOT_FOUND"));
        }

        McpToolExecutor executor = executorOpt.get();
        Tool tool = executor.getToolDefinition();
        String customParamPrompt = intentNode.getParamPromptTemplate();
        Map<String, Object> params = exactBusinessParameterFilter.filter(
                mcpParameterExtractor.extractParameters(question, tool, customParamPrompt));
        Set<String> entityKeys = new java.util.LinkedHashSet<>();
        Optional<String> entityTarget = entityResolver.targetFor(toolId);
        if (enableMissingGate && entityTarget.isPresent() && executionRecord != null
                && StrUtil.isNotBlank(userId) && StrUtil.isNotBlank(conversationId)) {
            String target = entityTarget.get();
            EntityResolutionResult entity = entityResolver.resolve(
                    new EntityResolutionRequest(toolId, target, question, userId));
            // The extractor may suggest an ID, but only a trusted candidate may bind an entity parameter.
            params.remove(target);
            executionRecord.setEntityResolution(ExecutionRecord.EntityResolutionRecord.builder()
                    .status(entity.status().name()).target(target)
                    .candidateCount(entity.candidates().size())
                    .selectedCandidateId(entity.selected() == null ? null : entity.selected().candidateId()).build());
            if (entity.status() == EntityResolutionStatus.UNIQUE) {
                Object binding = entity.selected().bindings().get(target);
                if (binding != null) {
                    clearAlternativeEntityParameters(target, params);
                    params.put(target, binding);
                    entityKeys.add(target);
                }
            } else if (entity.status() == EntityResolutionStatus.AMBIGUOUS) {
                ToolParameterContract pendingContract = parameterContractRegistry.resolve(tool);
                ParameterCheckResult pendingCheck = missingParameterGate.check(pendingContract, params);
                ExecutionRecord.ParameterResolutionRecord ambiguous = resolveParameters(
                        toolId, tool, params, pendingContract, pendingCheck, entityKeys);
                ambiguous.setStatus(ExecutionRecord.ResolutionStatus.AMBIGUOUS);
                entitySelectionClosedLoopService.createPending(userId, conversationId, intentNode.getId(), toolId,
                        question, params, target, entity, executionRecord);
                return new ToolOutput(toolId, null, ambiguous, null);
            }
        }
        ToolParameterContract contract = parameterContractRegistry.resolve(tool);
        ParameterCheckResult parameterCheck = missingParameterGate.check(contract, params);
        ExecutionRecord.ParameterResolutionRecord resolution = resolveParameters(
                toolId, tool, params, contract, parameterCheck, entityKeys);
        if (enableMissingGate && !parameterCheck.ready() && executionRecord != null
                && StrUtil.isNotBlank(userId) && StrUtil.isNotBlank(conversationId)) {
            missingParameterClosedLoopService.createPending(userId, conversationId, intentNode.getId(), toolId,
                    question, params, parameterCheck, executionRecord);
            return new ToolOutput(toolId, null, resolution, null);
        }
        Map<String, Object> runtimeParams;
        try {
            runtimeParams = huaweiMallRuntimeArguments.bind(toolId, params);
        } catch (IllegalStateException identityUnavailable) {
            CallToolResult result = CallToolResult.builder()
                    .content(List.of(new TextContent("{\"status\":\"INTERNAL_ERROR\",\"data\":{},\"candidates\":[],\"error\":{\"code\":\"AUTH_MAPPING_UNAVAILABLE\",\"message\":\"Trusted Huawei Mall identity is not configured\"},\"meta\":{\"asOf\":\"2026-09-14T10:00:00+08:00\",\"timezone\":\"Asia/Shanghai\",\"datasetVersion\":\"huawei-mall-fixture-v1.0\"}}")))
                    .isError(true)
                    .build();
            return new ToolOutput(toolId, result, resolution,
                    execution(toolId, safeArguments(params, Map.of()), ExecutionRecord.ToolStatus.ERROR, "INTERNAL_ERROR"));
        }
        addAuthContext(resolution, runtimeParams);
        Map<String, Object> safeArguments = safeArguments(params, runtimeParams);
        CallToolResult result = executor.execute(runtimeParams);
        ExecutionRecord.ToolStatus status = result != null && !Boolean.TRUE.equals(result.isError())
                ? ExecutionRecord.ToolStatus.SUCCESS
                : ExecutionRecord.ToolStatus.ERROR;
        return new ToolOutput(toolId, result, resolution,
                execution(toolId, safeArguments, status, extractResultStatus(result)));
    }

    private ExecutionRecord.ParameterResolutionRecord resolveParameters(String toolId, Tool tool,
                                                                         Map<String, Object> params,
                                                                         ToolParameterContract contract,
                                                                         ParameterCheckResult check,
                                                                         Set<String> entityKeys) {
        Map<String, Object> actualParams = params == null ? Map.of() : params;
        Map<String, ExecutionRecord.ResolvedParameterRecord> resolved = new LinkedHashMap<>();
        Map<String, Object> properties = tool.inputSchema() == null ? Map.of() : tool.inputSchema().properties();
        actualParams.forEach((name, value) -> {
            Object definition = properties == null ? null : properties.get(name);
            Object defaultValue = definition instanceof Map<?, ?> map ? map.get("default") : null;
            ExecutionRecord.ParameterSource source = entityKeys.contains(name)
                    ? ExecutionRecord.ParameterSource.ENTITY_RESOLUTION
                    : defaultValue != null && Objects.equals(defaultValue, value)
                            ? ExecutionRecord.ParameterSource.DEFAULT : ExecutionRecord.ParameterSource.USER_QUERY;
            resolved.put(name, ExecutionRecord.ResolvedParameterRecord.builder().value(value).source(source).build());
        });
        List<String> required = new java.util.ArrayList<>(contract.requiredAll());
        contract.requiredAny().forEach(group -> required.add(String.join("|", group.alternatives())));
        return ExecutionRecord.ParameterResolutionRecord.builder()
                .tool(toolId).required(required).resolved(resolved)
                .missing(new java.util.ArrayList<>(check.missingKeys()))
                .status(check.ready() ? ExecutionRecord.ResolutionStatus.RESOLVED
                        : ExecutionRecord.ResolutionStatus.MISSING).build();
    }

    private void clearAlternativeEntityParameters(String target, Map<String, Object> params) {
        if ("productId".equals(target)) {
            params.remove("model");
        }
    }

    private void addAuthContext(ExecutionRecord.ParameterResolutionRecord resolution,
                                Map<String, Object> runtimeParams) {
        Object principal = runtimeParams.get("_runtimePrincipal");
        if (principal != null) {
            resolution.getResolved().put("principalId", ExecutionRecord.ResolvedParameterRecord.builder()
                    .value(principal)
                    .source(ExecutionRecord.ParameterSource.AUTH_CONTEXT)
                    .build());
        }
    }

    private Map<String, Object> safeArguments(Map<String, Object> params, Map<String, Object> runtimeParams) {
        Map<String, Object> safe = new LinkedHashMap<>(params == null ? Map.of() : params);
        Object principal = runtimeParams.get("_runtimePrincipal");
        if (principal != null) {
            safe.put("principalId", principal);
        }
        return safe;
    }

    private String extractResultStatus(CallToolResult result) {
        if (result == null) {
            return null;
        }
        if (result.structuredContent() instanceof Map<?, ?> structured) {
            Object status = structured.get("status");
            if (status != null) {
                return status.toString();
            }
        }
        if (result.content() != null) {
            for (var content : result.content()) {
                if (content instanceof TextContent textContent) {
                    try {
                        String status = JSONUtil.parseObj(textContent.text()).getStr("status");
                        if (StrUtil.isNotBlank(status)) {
                            return status;
                        }
                    } catch (Exception ignored) {
                        // Non-JSON tool text has no business status.
                    }
                }
            }
        }
        return Boolean.TRUE.equals(result.isError()) ? "ERROR" : "SUCCESS";
    }

    private static ExecutionRecord.ToolExecutionRecord execution(String toolId,
                                                                  Map<String, Object> arguments,
                                                                  ExecutionRecord.ToolStatus status,
                                                                  String resultStatus) {
        return ExecutionRecord.ToolExecutionRecord.builder()
                .tool(toolId)
                .arguments(arguments)
                .status(status)
                .resultStatus(resultStatus)
                .build();
    }

    private record ToolOutput(String toolId,
                              CallToolResult result,
                              ExecutionRecord.ParameterResolutionRecord resolution,
                              ExecutionRecord.ToolExecutionRecord execution) {
    }

    private record SubQuestionContext(String question,
                                      List<EvidenceRecord> evidenceRecords,
                                      String mcpContext) {
    }
}
