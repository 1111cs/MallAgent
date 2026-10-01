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

package com.nageoffer.ai.ragent.rag.core.entity;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.rag.core.clarification.CancelMatcher;
import com.nageoffer.ai.ragent.rag.core.clarification.ClarificationPromptBuilder;
import com.nageoffer.ai.ragent.rag.core.clarification.MissingParameterGate;
import com.nageoffer.ai.ragent.rag.core.clarification.ParameterCheckResult;
import com.nageoffer.ai.ragent.rag.core.clarification.PendingClarificationState;
import com.nageoffer.ai.ragent.rag.core.clarification.PendingClarificationStore;
import com.nageoffer.ai.ragent.rag.core.clarification.PendingReason;
import com.nageoffer.ai.ragent.rag.core.clarification.ToolParameterContract;
import com.nageoffer.ai.ragent.rag.core.clarification.ToolParameterContractRegistry;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.mcp.HuaweiMallRuntimeArguments;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import com.nageoffer.ai.ragent.rag.core.prompt.ContextFormatter;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EntitySelectionClosedLoopService {
    private final PendingClarificationStore store;
    private final CancelMatcher cancelMatcher;
    private final SelectionResolver selectionResolver;
    private final SelectionPromptBuilder selectionPromptBuilder;
    private final EntityResolver entityResolver;
    private final McpToolRegistry toolRegistry;
    private final ToolParameterContractRegistry contracts;
    private final MissingParameterGate gate;
    private final ClarificationPromptBuilder clarificationPromptBuilder;
    private final HuaweiMallRuntimeArguments runtimeArguments;
    private final ContextFormatter contextFormatter;

    public String createPending(String userId, String conversationId, String intent, String toolId,
                                String originalQuery, Map<String, Object> arguments,
                                String selectionTarget, EntityResolutionResult entity,
                                ExecutionRecord record) {
        String pendingTaskId = "PT-" + IdUtil.fastSimpleUUID();
        PendingClarificationState state = PendingClarificationState.builder()
                .pendingTaskId(pendingTaskId).authenticatedUserId(userId).conversationId(conversationId)
                .intent(intent).toolId(toolId).originalQuery(originalQuery)
                .resolvedArguments(new LinkedHashMap<>(arguments == null ? Map.of() : arguments))
                .pendingReason(PendingReason.NEED_SELECTION).selectionTarget(selectionTarget)
                .entityType(entity.entityType()).candidates(entity.candidates()).build();
        store.saveWithTtl(state);
        record.setTaskStatus(ExecutionRecord.TaskStatus.NEED_SELECTION);
        record.setPendingTaskId(pendingTaskId);
        record.setClarificationAction(ExecutionRecord.ClarificationAction.CREATED);
        record.setEntityResolution(entityRecord(entity, selectionTarget, null));
        String prompt = selectionPromptBuilder.build(entity.entityType(), entity.candidates());
        record.setDirectResponse(prompt);
        return prompt;
    }

    public Optional<ResumeOutcome> resume(String userId, String conversationId, String input,
                                          ExecutionRecord record) {
        Optional<PendingClarificationState> pendingOpt = store.get(userId, conversationId);
        if (pendingOpt.isEmpty() || pendingOpt.get().getPendingReason() != PendingReason.NEED_SELECTION)
            return Optional.empty();
        PendingClarificationState pending = pendingOpt.get();
        record.setPendingTaskId(pending.getPendingTaskId());
        addRoute(record, pending);
        if (cancelMatcher.matches(input)) {
            store.cancel(userId, conversationId);
            record.setTaskStatus(ExecutionRecord.TaskStatus.CANCELLED);
            record.setClarificationAction(ExecutionRecord.ClarificationAction.CANCELLED);
            return Optional.of(ResumeOutcome.direct("好的，已取消这次查询。"));
        }

        Optional<EntityCandidate> selected = selectionResolver.resolve(input, pending.getCandidates());
        if (selected.isEmpty()) return Optional.of(continueSelection(pending, record));
        EntityResolutionRequest request = new EntityResolutionRequest(
                pending.getToolId(), pending.getSelectionTarget(), pending.getOriginalQuery(), userId);
        Optional<EntityCandidate> current = entityResolver.revalidate(request, selected.get().candidateId());
        if (current.isEmpty()) {
            store.cancel(userId, conversationId);
            record.setTaskStatus(ExecutionRecord.TaskStatus.CANCELLED);
            return Optional.of(ResumeOutcome.direct("该候选当前已不可用，请重新发起查询。"));
        }

        Optional<McpToolExecutor> executorOpt = toolRegistry.getExecutor(pending.getToolId());
        if (executorOpt.isEmpty()) {
            store.cancel(userId, conversationId);
            record.setTaskStatus(ExecutionRecord.TaskStatus.CANCELLED);
            return Optional.of(ResumeOutcome.direct("原查询工具当前不可用，请重新发起查询。"));
        }
        McpToolExecutor executor = executorOpt.get();
        Tool tool = executor.getToolDefinition();
        Object binding = current.get().bindings().get(pending.getSelectionTarget());
        if (binding == null) return Optional.of(continueSelection(pending, record));
        Map<String, Object> merged = new LinkedHashMap<>(pending.getResolvedArguments());
        if ("productId".equals(pending.getSelectionTarget())) merged.remove("model");
        merged.put(pending.getSelectionTarget(), binding);
        Set<String> entityKeys = new LinkedHashSet<>(pending.getEntityResolutionParameters());
        entityKeys.add(pending.getSelectionTarget());
        ToolParameterContract contract = contracts.resolve(tool);
        ParameterCheckResult check = gate.check(contract, merged);
        ExecutionRecord.ParameterResolutionRecord resolution = resolution(
                tool.name(), contract, check, merged, pending.getClarificationParameters(), entityKeys);
        record.setEntityResolution(ExecutionRecord.EntityResolutionRecord.builder()
                .status(EntityResolutionStatus.UNIQUE.name()).target(pending.getSelectionTarget())
                .candidateCount(pending.getCandidates().size())
                .selectedCandidateId(current.get().candidateId()).build());

        if (!check.ready()) {
            pending.setResolvedArguments(merged);
            pending.setEntityResolutionParameters(entityKeys);
            pending.setMissingRequirements(check);
            pending.setPendingReason(PendingReason.NEED_PARAMETERS);
            pending.setCandidates(List.of());
            store.saveWithTtl(pending);
            record.addToolObservation(resolution, null);
            record.setTaskStatus(ExecutionRecord.TaskStatus.NEED_CLARIFICATION);
            record.setClarificationAction(ExecutionRecord.ClarificationAction.UPDATED);
            return Optional.of(ResumeOutcome.direct(clarificationPromptBuilder.build(check)));
        }

        if (store.take(userId, conversationId).isEmpty()) {
            record.setTaskStatus(ExecutionRecord.TaskStatus.CANCELLED);
            return Optional.of(ResumeOutcome.direct("当前选择任务已处理或过期，请重新发起查询。"));
        }
        Map<String, Object> runtime = runtimeArguments.bind(tool.name(), merged);
        addAuthContext(resolution, runtime);
        CallToolResult result = executor.execute(runtime);
        ExecutionRecord.ToolStatus status = result != null && !Boolean.TRUE.equals(result.isError())
                ? ExecutionRecord.ToolStatus.SUCCESS : ExecutionRecord.ToolStatus.ERROR;
        record.addToolObservation(resolution, ExecutionRecord.ToolExecutionRecord.builder()
                .tool(tool.name()).arguments(safeArguments(merged, runtime)).status(status)
                .resultStatus(resultStatus(result)).build());
        record.setTaskStatus(ExecutionRecord.TaskStatus.COMPLETED);
        record.setClarificationAction(ExecutionRecord.ClarificationAction.RESUMED_AND_CLEARED);
        return Optional.of(retrievalOutcome(pending, tool, result));
    }

    private ResumeOutcome continueSelection(PendingClarificationState pending, ExecutionRecord record) {
        record.setTaskStatus(ExecutionRecord.TaskStatus.NEED_SELECTION);
        record.setClarificationAction(ExecutionRecord.ClarificationAction.UPDATED);
        record.setEntityResolution(ExecutionRecord.EntityResolutionRecord.builder()
                .status(EntityResolutionStatus.AMBIGUOUS.name()).target(pending.getSelectionTarget())
                .candidateCount(pending.getCandidates().size()).build());
        return ResumeOutcome.direct(selectionPromptBuilder.build(pending.getEntityType(), pending.getCandidates()));
    }

    private ExecutionRecord.EntityResolutionRecord entityRecord(EntityResolutionResult result,
                                                                  String target, String selectedId) {
        return ExecutionRecord.EntityResolutionRecord.builder().status(result.status().name()).target(target)
                .candidateCount(result.candidates().size()).selectedCandidateId(selectedId).build();
    }

    private ExecutionRecord.ParameterResolutionRecord resolution(String toolId, ToolParameterContract contract,
            ParameterCheckResult check, Map<String, Object> args, Set<String> clarificationKeys,
            Set<String> entityKeys) {
        Map<String, ExecutionRecord.ResolvedParameterRecord> resolved = new LinkedHashMap<>();
        args.forEach((key, value) -> {
            ExecutionRecord.ParameterSource source = entityKeys.contains(key)
                    ? ExecutionRecord.ParameterSource.ENTITY_RESOLUTION
                    : clarificationKeys.contains(key) ? ExecutionRecord.ParameterSource.CLARIFICATION_INPUT
                    : ExecutionRecord.ParameterSource.USER_QUERY;
            resolved.put(key, ExecutionRecord.ResolvedParameterRecord.builder().value(value).source(source).build());
        });
        List<String> required = new ArrayList<>(contract.requiredAll());
        contract.requiredAny().forEach(group -> required.add(String.join("|", group.alternatives())));
        return ExecutionRecord.ParameterResolutionRecord.builder().tool(toolId).required(required)
                .resolved(resolved).missing(new ArrayList<>(check.missingKeys()))
                .status(check.ready() ? ExecutionRecord.ResolutionStatus.RESOLVED
                        : ExecutionRecord.ResolutionStatus.MISSING).build();
    }

    private ResumeOutcome retrievalOutcome(PendingClarificationState pending, Tool tool, CallToolResult result) {
        IntentNode node = IntentNode.builder().id(pending.getIntent()).kind(IntentKind.MCP)
                .mcpToolId(tool.name()).build();
        NodeScore score = NodeScore.builder().node(node).score(1D).build();
        String mcpContext = contextFormatter.formatMcpContext(Map.of(tool.name(), List.of(result)), List.of(score));
        RetrievalContext retrieval = RetrievalContext.builder().mcpContext(mcpContext).kbContext("")
                .intentChunks(Map.of()).build();
        return ResumeOutcome.retrieval(retrieval, pending.getOriginalQuery(),
                List.of(new SubQuestionIntent(pending.getOriginalQuery(), List.of(score))));
    }

    private void addRoute(ExecutionRecord record, PendingClarificationState state) {
        record.addRoute(ExecutionRecord.RouteRecord.builder().subQuestion(state.getOriginalQuery())
                .intent(state.getIntent()).confidence(1D)
                .resource(ExecutionRecord.ResourceRecord.builder().type("MCP").name(state.getToolId()).build())
                .build());
    }

    private void addAuthContext(ExecutionRecord.ParameterResolutionRecord resolution, Map<String, Object> runtime) {
        Object principal = runtime.get("_runtimePrincipal");
        if (principal != null) resolution.getResolved().put("principalId",
                ExecutionRecord.ResolvedParameterRecord.builder().value(principal)
                        .source(ExecutionRecord.ParameterSource.AUTH_CONTEXT).build());
    }

    private Map<String, Object> safeArguments(Map<String, Object> args, Map<String, Object> runtime) {
        Map<String, Object> safe = new LinkedHashMap<>(args);
        if (runtime.get("_runtimePrincipal") != null) safe.put("principalId", runtime.get("_runtimePrincipal"));
        return safe;
    }

    private String resultStatus(CallToolResult result) {
        if (result == null) return null;
        if (result.structuredContent() instanceof Map<?, ?> structured && structured.get("status") != null)
            return structured.get("status").toString();
        if (result.content() != null) for (var content : result.content()) if (content instanceof TextContent text) {
            try {
                String value = JSONUtil.parseObj(text.text()).getStr("status");
                if (StrUtil.isNotBlank(value)) return value;
            } catch (Exception ignored) { }
        }
        return Boolean.TRUE.equals(result.isError()) ? "ERROR" : "SUCCESS";
    }

    public record ResumeOutcome(String directResponse, RetrievalContext retrievalContext,
                                String originalQuery, List<SubQuestionIntent> subIntents) {
        public static ResumeOutcome direct(String text) { return new ResumeOutcome(text, null, null, List.of()); }
        public static ResumeOutcome retrieval(RetrievalContext context, String query, List<SubQuestionIntent> intents) {
            return new ResumeOutcome(null, context, query, intents);
        }
        public boolean isDirect() { return directResponse != null; }
    }
}
