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

package com.nageoffer.ai.ragent.rag.core.dependency;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.core.clarification.*;
import com.nageoffer.ai.ragent.rag.core.entity.*;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScoreFilters;
import com.nageoffer.ai.ragent.rag.core.mcp.*;
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

import java.util.*;

@Service
@RequiredArgsConstructor
public class DependencyExecutionCoordinator {
    private final DependencySourceMatcher sourceMatcher;
    private final DependencyContractRegistry contractRegistry;
    private final McpToolRegistry toolRegistry;
    private final McpParameterExtractor parameterExtractor;
    private final ExactBusinessParameterFilter parameterFilter;
    private final HuaweiMallRuntimeArguments runtimeArguments;
    private final ToolParameterContractRegistry parameterContracts;
    private final MissingParameterGate missingGate;
    private final MissingParameterClosedLoopService missingClosedLoop;
    private final PendingClarificationStore pendingStore;
    private final SelectionResolver selectionResolver;
    private final SelectionPromptBuilder selectionPromptBuilder;
    private final CancelMatcher cancelMatcher;
    private final ContextFormatter contextFormatter;
    private final ObjectMapper objectMapper;

    public Optional<Outcome> execute(String originalQuery, List<SubQuestionIntent> subIntents,
                                     ExecutionRecord record, String userId, String conversationId) {
        Optional<DependencySourceConstraint> sourceOpt = sourceMatcher.match(originalQuery);
        if (sourceOpt.isEmpty()) return Optional.empty();
        Map<String, NodeScore> targets = targetScores(subIntents);
        Optional<DependencyContract> contractOpt = contractRegistry.resolve(targets.keySet(), sourceOpt.get());
        if (contractOpt.isEmpty()) return Optional.empty();
        DependencyContract contract = contractOpt.get();
        List<DownstreamTask> tasks = extractTasks(originalQuery, contract, targets);
        return Optional.of(runPlan(originalQuery, contract, sourceOpt.get(), tasks, targets,
                record, userId, conversationId, false));
    }

    public Optional<Outcome> resume(String userId, String conversationId, String input,
                                    ExecutionRecord record) {
        Optional<PendingClarificationState> stateOpt = pendingStore.get(userId, conversationId);
        if (stateOpt.isEmpty() || stateOpt.get().getDependencyContext() == null) return Optional.empty();
        PendingClarificationState state = stateOpt.get();
        if (cancelMatcher.matches(input)) {
            pendingStore.cancel(userId, conversationId);
            record.setTaskStatus(ExecutionRecord.TaskStatus.CANCELLED);
            record.setClarificationAction(ExecutionRecord.ClarificationAction.CANCELLED);
            return Optional.of(Outcome.direct("好的，已取消这次查询。"));
        }
        Optional<EntityCandidate> selected = selectionResolver.resolve(input, state.getCandidates());
        if (selected.isEmpty()) {
            record.setTaskStatus(ExecutionRecord.TaskStatus.NEED_SELECTION);
            return Optional.of(Outcome.direct(selectionPromptBuilder.build(state.getEntityType(), state.getCandidates())));
        }
        DependencyPendingContext pending = state.getDependencyContext();
        DependencySourceConstraint source = new DependencySourceConstraint(
                String.valueOf(pending.normalizedUpstreamConstraints().get("timeRange")),
                String.valueOf(pending.normalizedUpstreamConstraints().get("category")));
        DependencyContract contract = new DependencyContract(pending.dependencyContractId(), pending.upstreamTool(),
                source.category(), pending.downstreamTasks().stream().map(DownstreamTask::toolId).toList(),
                pending.bindingDefinitions());
        Map<String, NodeScore> scores = syntheticScores(pending.downstreamTasks());
        UpstreamResult revalidated = executeUpstream(source, record);
        Optional<EntityCandidate> current = revalidated.candidates().stream()
                .filter(item -> item.candidateId().equals(selected.get().candidateId())).findFirst();
        if (!"OK".equals(revalidated.status()) && !"MULTIPLE_MATCHES".equals(revalidated.status())
                || current.isEmpty()) {
            pendingStore.cancel(userId, conversationId);
            record.setTaskStatus(ExecutionRecord.TaskStatus.TERMINATED);
            return Optional.of(Outcome.direct("所选购买记录当前已不可用，请重新发起查询。"));
        }
        pendingStore.take(userId, conversationId);
        record.setClarificationAction(ExecutionRecord.ClarificationAction.RESUMED_AND_CLEARED);
        return Optional.of(executeDownstream(state.getOriginalQuery(), contract, pending.downstreamTasks(),
                current.get(), scores, record, userId, conversationId));
    }

    private Outcome runPlan(String query, DependencyContract contract, DependencySourceConstraint source,
                            List<DownstreamTask> tasks, Map<String, NodeScore> scores,
                            ExecutionRecord record, String userId, String conversationId, boolean resume) {
        recordToolPlan(record, contract);
        UpstreamResult upstream = executeUpstream(source, record);
        if (Set.of("NOT_FOUND", "VALIDATION_ERROR", "CONFLICT", "INTERNAL_ERROR").contains(upstream.status())) {
            record.setTaskStatus(ExecutionRecord.TaskStatus.TERMINATED);
            record.setDirectResponse("未找到符合条件的购买记录，未继续执行后续查询。");
            return Outcome.direct(record.getDirectResponse());
        }
        if (upstream.candidates().isEmpty()) {
            record.setTaskStatus(ExecutionRecord.TaskStatus.TERMINATED);
            record.setDirectResponse("未找到符合条件的购买记录，未继续执行后续查询。");
            return Outcome.direct(record.getDirectResponse());
        }
        if (upstream.candidates().size() > 1) {
            String pendingId = "PT-" + IdUtil.fastSimpleUUID();
            DependencyPendingContext dependency = new DependencyPendingContext(contract.id(), contract.upstreamTool(),
                    Map.of("timeRange", source.timeRange(), "category", source.category()), tasks, contract.bindings(), upstream.candidates());
            PendingClarificationState state = PendingClarificationState.builder().pendingTaskId(pendingId)
                    .authenticatedUserId(userId).conversationId(conversationId).originalQuery(query)
                    .pendingReason(PendingReason.NEED_SELECTION).selectionTarget("dependencyCandidate")
                    .entityType("DEVICE").candidates(upstream.candidates()).dependencyContext(dependency).build();
            pendingStore.saveWithTtl(state);
            record.setPendingTaskId(pendingId);
            record.setTaskStatus(ExecutionRecord.TaskStatus.NEED_SELECTION);
            record.setClarificationAction(ExecutionRecord.ClarificationAction.CREATED);
            record.setEntityResolution(ExecutionRecord.EntityResolutionRecord.builder()
                    .status(EntityResolutionStatus.AMBIGUOUS.name()).target("dependencyCandidate")
                    .candidateCount(upstream.candidates().size()).build());
            String prompt = selectionPromptBuilder.build("DEVICE", upstream.candidates());
            record.setDirectResponse(prompt);
            return Outcome.direct(prompt);
        }
        return executeDownstream(query, contract, tasks, upstream.candidates().get(0), scores,
                record, userId, conversationId);
    }

    private UpstreamResult executeUpstream(DependencySourceConstraint source, ExecutionRecord record) {
        McpToolExecutor executor = toolRegistry.getExecutor("getOrder").orElseThrow();
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("timeRange", source.timeRange());
        safe.put("category", source.category());
        Map<String, Object> runtime = runtimeArguments.bind("getOrder", safe);
        CallToolResult result = executor.execute(runtime);
        Map<String, Object> envelope = envelope(result);
        String status = String.valueOf(envelope.getOrDefault("status", Boolean.TRUE.equals(result.isError()) ? "INTERNAL_ERROR" : "OK"));
        ExecutionRecord.ParameterResolutionRecord resolution = resolution("getOrder", safe, Map.of());
        addAuth(resolution, runtime);
        record.addToolObservation(resolution, execution("getOrder", safe, result, status));
        return new UpstreamResult(status, candidates(envelope, source.category()), result);
    }

    private Outcome executeDownstream(String query, DependencyContract contract, List<DownstreamTask> tasks,
                                      EntityCandidate candidate, Map<String, NodeScore> scores,
                                      ExecutionRecord record, String userId, String conversationId) {
        Map<String, List<CallToolResult>> results = new LinkedHashMap<>();
        for (DownstreamTask task : tasks) {
            McpToolExecutor executor = toolRegistry.getExecutor(task.toolId()).orElseThrow();
            Tool tool = executor.getToolDefinition();
            Map<String, Object> args = new LinkedHashMap<>(task.safeArguments());
            for (ToolOutputBinding binding : contract.bindings()) {
                if (!binding.toTool().equals(task.toolId())) continue;
                Object value = candidate.bindings().get(binding.toParameter());
                if (value != null) {
                    if ("productId".equals(binding.toParameter())) args.remove("model");
                    args.put(binding.toParameter(), value);
                    record.addToolBinding(ExecutionRecord.ToolBindingRecord.builder()
                            .fromTool(binding.fromTool()).fromField(binding.fromField())
                            .toTool(binding.toTool()).toParameter(binding.toParameter())
                            .source(ExecutionRecord.ParameterSource.TOOL_OUTPUT).build());
                }
            }
            ToolParameterContract parameterContract = parameterContracts.resolve(tool);
            ParameterCheckResult check = missingGate.check(parameterContract, args);
            Map<String, ExecutionRecord.ParameterSource> sources = new LinkedHashMap<>();
            args.keySet().forEach(key -> sources.put(key, ExecutionRecord.ParameterSource.USER_QUERY));
            contract.bindings().stream().filter(b -> b.toTool().equals(task.toolId()))
                    .forEach(b -> sources.put(b.toParameter(), ExecutionRecord.ParameterSource.TOOL_OUTPUT));
            ExecutionRecord.ParameterResolutionRecord resolution = resolution(task.toolId(), args, sources);
            resolution.setStatus(check.ready() ? ExecutionRecord.ResolutionStatus.RESOLVED : ExecutionRecord.ResolutionStatus.MISSING);
            resolution.setMissing(new ArrayList<>(check.missingKeys()));
            if (!check.ready()) {
                missingClosedLoop.createPending(userId, conversationId, task.intent(), task.toolId(),
                        query, args, check, record);
                record.addToolObservation(resolution, null);
                return Outcome.direct(record.getDirectResponse());
            }
            Map<String, Object> runtime = runtimeArguments.bind(task.toolId(), args);
            CallToolResult result = executor.execute(runtime);
            String status = businessStatus(result);
            addAuth(resolution, runtime);
            record.addToolObservation(resolution, execution(task.toolId(), args, result, status));
            results.put(task.toolId(), List.of(result));
        }
        record.setTaskStatus(ExecutionRecord.TaskStatus.COMPLETED);
        List<NodeScore> orderedScores = tasks.stream().map(t -> scores.get(t.toolId())).filter(Objects::nonNull).toList();
        String context = contextFormatter.formatMcpContext(results, orderedScores);
        return Outcome.retrieval(RetrievalContext.builder().mcpContext(context).kbContext("")
                .intentChunks(Map.of()).build());
    }

    private List<DownstreamTask> extractTasks(String query, DependencyContract contract,
                                              Map<String, NodeScore> scores) {
        List<DownstreamTask> tasks = new ArrayList<>();
        for (String toolId : contract.downstreamTools()) {
            McpToolExecutor executor = toolRegistry.getExecutor(toolId).orElseThrow();
            IntentNode node = scores.get(toolId).getNode();
            Map<String, Object> args = parameterFilter.filter(parameterExtractor.extractParameters(
                    query, executor.getToolDefinition(), node.getParamPromptTemplate()));
            contract.bindings().stream().filter(b -> b.toTool().equals(toolId))
                    .forEach(b -> args.remove(b.toParameter()));
            tasks.add(new DownstreamTask(node.getId(), toolId, args));
        }
        return tasks;
    }

    private Map<String, NodeScore> targetScores(List<SubQuestionIntent> intents) {
        Map<String, NodeScore> result = new LinkedHashMap<>();
        for (SubQuestionIntent intent : intents) for (NodeScore score : NodeScoreFilters.mcp(intent.nodeScores()))
            result.putIfAbsent(score.getNode().getMcpToolId(), score);
        return result;
    }

    private Map<String, NodeScore> syntheticScores(List<DownstreamTask> tasks) {
        Map<String, NodeScore> result = new LinkedHashMap<>();
        for (DownstreamTask task : tasks) {
            IntentNode node = IntentNode.builder().id(task.intent()).kind(IntentKind.MCP).mcpToolId(task.toolId()).build();
            result.put(task.toolId(), NodeScore.builder().node(node).score(1D).build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<EntityCandidate> candidates(Map<String, Object> envelope, String category) {
        List<Map<String, Object>> orders = new ArrayList<>();
        Object status = envelope.get("status");
        if ("OK".equals(status) && envelope.get("data") instanceof Map<?, ?> data
                && data.get("orders") instanceof List<?> list)
            list.forEach(item -> { if (item instanceof Map<?, ?> map) orders.add((Map<String, Object>) map); });
        if ("MULTIPLE_MATCHES".equals(status) && envelope.get("candidates") instanceof List<?> list)
            list.forEach(item -> { if (item instanceof Map<?, ?> map) orders.add((Map<String, Object>) map); });
        List<EntityCandidate> result = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            Object productsObject = order.get("products");
            if (!(productsObject instanceof List<?> products)) continue;
            for (Object item : products) {
                if (!(item instanceof Map<?, ?> raw) || !category.equals(String.valueOf(raw.get("category")))) continue;
                Map<String, Object> bindings = new LinkedHashMap<>();
                put(bindings, "sn", raw.get("sn")); put(bindings, "productId", raw.get("productId"));
                put(bindings, "category", raw.get("category")); put(bindings, "orderNo", order.get("orderNo"));
                String id = String.valueOf(order.get("orderNo")) + ":" + String.valueOf(raw.get("productId"))
                        + ":" + String.valueOf(raw.get("sn"));
                String label = String.valueOf(raw.get("productName")) + "（订单 " + order.get("orderNo") + "）";
                result.add(new EntityCandidate(id, label, bindings));
            }
        }
        return result;
    }

    private void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }

    private Map<String, Object> envelope(CallToolResult result) {
        if (result == null) return Map.of("status", "INTERNAL_ERROR");
        try {
            if (result.structuredContent() != null)
                return objectMapper.convertValue(result.structuredContent(), new TypeReference<>() { });
            if (result.content() != null) for (var content : result.content()) if (content instanceof TextContent text)
                return objectMapper.readValue(text.text(), new TypeReference<>() { });
        } catch (Exception ignored) { }
        return Map.of("status", Boolean.TRUE.equals(result.isError()) ? "INTERNAL_ERROR" : "OK");
    }

    private String businessStatus(CallToolResult result) {
        return String.valueOf(envelope(result).getOrDefault("status", "UNKNOWN"));
    }

    private ExecutionRecord.ParameterResolutionRecord resolution(String tool, Map<String, Object> args,
            Map<String, ExecutionRecord.ParameterSource> sources) {
        Map<String, ExecutionRecord.ResolvedParameterRecord> resolved = new LinkedHashMap<>();
        args.forEach((key, value) -> resolved.put(key, ExecutionRecord.ResolvedParameterRecord.builder()
                .value(value).source(sources.getOrDefault(key, ExecutionRecord.ParameterSource.USER_QUERY)).build()));
        return ExecutionRecord.ParameterResolutionRecord.builder().tool(tool).resolved(resolved)
                .status(ExecutionRecord.ResolutionStatus.RESOLVED).build();
    }

    private void addAuth(ExecutionRecord.ParameterResolutionRecord resolution, Map<String, Object> runtime) {
        if (runtime.get("_runtimePrincipal") != null) resolution.getResolved().put("principalId",
                ExecutionRecord.ResolvedParameterRecord.builder().value(runtime.get("_runtimePrincipal"))
                        .source(ExecutionRecord.ParameterSource.AUTH_CONTEXT).build());
    }

    private ExecutionRecord.ToolExecutionRecord execution(String tool, Map<String, Object> args,
                                                            CallToolResult result, String status) {
        return ExecutionRecord.ToolExecutionRecord.builder().tool(tool).arguments(new LinkedHashMap<>(args))
                .status(result != null && !Boolean.TRUE.equals(result.isError())
                        ? ExecutionRecord.ToolStatus.SUCCESS : ExecutionRecord.ToolStatus.ERROR)
                .resultStatus(status).build();
    }

    private void recordToolPlan(ExecutionRecord record, DependencyContract contract) {
        if (!record.getToolPlan().isEmpty()) return;
        record.getToolPlan().add(ExecutionRecord.ToolPlanStep.builder().step(1)
                .tools(List.of(contract.upstreamTool())).build());
        record.getToolPlan().add(ExecutionRecord.ToolPlanStep.builder().step(2)
                .tools(contract.downstreamTools()).build());
    }

    private record UpstreamResult(String status, List<EntityCandidate> candidates, CallToolResult raw) { }

    public record Outcome(String directResponse, RetrievalContext retrievalContext) {
        public static Outcome direct(String value) { return new Outcome(value, null); }
        public static Outcome retrieval(RetrievalContext value) { return new Outcome(null, value); }
        public boolean isDirect() { return directResponse != null; }
    }
}


