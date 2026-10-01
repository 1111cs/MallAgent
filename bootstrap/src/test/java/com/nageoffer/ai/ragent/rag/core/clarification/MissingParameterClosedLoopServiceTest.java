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

package com.nageoffer.ai.ragent.rag.core.clarification;

import com.nageoffer.ai.ragent.rag.core.mcp.HuaweiMallRuntimeArguments;
import com.nageoffer.ai.ragent.rag.core.mcp.McpParameterExtractor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolExecutor;
import com.nageoffer.ai.ragent.rag.core.mcp.McpToolRegistry;
import com.nageoffer.ai.ragent.rag.core.prompt.ContextFormatter;
import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MissingParameterClosedLoopServiceTest {

    @Test
    void resumesSamePendingTaskRebindsAuthAndClearsBeforeToolExecution() {
        InMemoryStore store = new InMemoryStore();
        McpToolRegistry registry = mock(McpToolRegistry.class);
        McpParameterExtractor extractor = mock(McpParameterExtractor.class);
        HuaweiMallRuntimeArguments runtime = mock(HuaweiMallRuntimeArguments.class);
        ContextFormatter formatter = mock(ContextFormatter.class);
        McpToolExecutor executor = mock(McpToolExecutor.class);
        Tool tool = tool("getWarrantyStatus", Map.of("sn", Map.of("type", "string")), List.of("sn"));
        when(registry.getExecutor("getWarrantyStatus")).thenReturn(Optional.of(executor));
        when(executor.getToolDefinition()).thenReturn(tool);
        when(extractor.extractParameters(eq("SN 是 ABC001"), any(Tool.class))).thenReturn(Map.of("sn", "ABC001"));
        when(runtime.bind(eq("getWarrantyStatus"), anyMap())).thenAnswer(invocation -> {
            Map<String, Object> result = new LinkedHashMap<>(invocation.getArgument(1));
            result.put("_runtimePrincipal", "U001");
            return result;
        });
        when(executor.execute(anyMap())).thenAnswer(invocation -> {
            assertTrue(store.state.isEmpty(), "Pending must be cleared before terminal tool execution");
            return ok();
        });
        when(formatter.formatMcpContext(anyMap(), anyList())).thenReturn("tool context");

        MissingParameterClosedLoopService service = service(store, registry, extractor, runtime, formatter);
        ExecutionRecord first = new ExecutionRecord();
        ParameterCheckResult missing = new ParameterCheckResult(false, Set.of("sn"), List.of());
        service.createPending("9001", "conv-1", "WARRANTY_STATUS", "getWarrantyStatus",
                "帮我查设备保修", Map.of(), missing, first);
        String pendingId = first.getPendingTaskId();
        assertEquals(ExecutionRecord.TaskStatus.NEED_CLARIFICATION, first.getTaskStatus());
        verifyNoInteractions(executor);

        ExecutionRecord second = new ExecutionRecord();
        var outcome = service.resume("9001", "conv-1", "SN 是 ABC001", second).orElseThrow();
        assertFalse(outcome.isDirect());
        assertEquals(pendingId, second.getPendingTaskId());
        assertEquals(ExecutionRecord.TaskStatus.COMPLETED, second.getTaskStatus());
        assertEquals(ExecutionRecord.ClarificationAction.RESUMED_AND_CLEARED, second.getClarificationAction());
        assertTrue(store.state.isEmpty());
        assertEquals(1, second.getToolExecutions().size());
        assertEquals(ExecutionRecord.ParameterSource.CLARIFICATION_INPUT,
                second.getParameterResolutions().get(0).getResolved().get("sn").getSource());
        assertEquals(ExecutionRecord.ParameterSource.AUTH_CONTEXT,
                second.getParameterResolutions().get(0).getResolved().get("principalId").getSource());
    }

    @Test
    void keepsSamePendingTaskWhenOnlyPartOfRequirementsArriveAndSupportsCancel() {
        InMemoryStore store = new InMemoryStore();
        McpToolRegistry registry = mock(McpToolRegistry.class);
        McpParameterExtractor extractor = mock(McpParameterExtractor.class);
        HuaweiMallRuntimeArguments runtime = mock(HuaweiMallRuntimeArguments.class);
        ContextFormatter formatter = mock(ContextFormatter.class);
        McpToolExecutor executor = mock(McpToolExecutor.class);
        Tool tool = tool("getCouponEligibility", Map.of(
                "couponId", Map.of("type", "string"), "orderNo", Map.of("type", "string"),
                "productIds", Map.of("type", "array")), List.of("couponId"));
        when(registry.getExecutor("getCouponEligibility")).thenReturn(Optional.of(executor));
        when(executor.getToolDefinition()).thenReturn(tool);
        when(extractor.extractParameters(anyString(), any(Tool.class))).thenReturn(Map.of("couponId", "C001"));

        MissingParameterClosedLoopService service = service(store, registry, extractor, runtime, formatter);
        var contract = new ToolParameterContract(Set.of("couponId"),
                List.of(new RequiredAnyGroup(Set.of("orderNo", "productIds"), "订单号或商品")));
        var missing = new MissingParameterGate().check(contract, Map.of());
        ExecutionRecord created = new ExecutionRecord();
        service.createPending("9001", "conv-2", "COUPON_ELIGIBILITY",
                "getCouponEligibility", "优惠券为什么不能用", Map.of(), missing, created);
        String pendingId = created.getPendingTaskId();

        ExecutionRecord partial = new ExecutionRecord();
        var partialOutcome = service.resume("9001", "conv-2", "优惠券是 C001", partial).orElseThrow();
        assertTrue(partialOutcome.isDirect());
        assertEquals(pendingId, partial.getPendingTaskId());
        assertEquals(ExecutionRecord.ClarificationAction.UPDATED, partial.getClarificationAction());
        assertTrue(store.state.isPresent());
        verifyNoInteractions(runtime, formatter);
        verify(executor, never()).execute(anyMap());

        ExecutionRecord cancelled = new ExecutionRecord();
        var cancelOutcome = service.resume("9001", "conv-2", "算了。", cancelled).orElseThrow();
        assertTrue(cancelOutcome.isDirect());
        assertEquals(ExecutionRecord.TaskStatus.CANCELLED, cancelled.getTaskStatus());
        assertTrue(store.state.isEmpty());
    }

    private MissingParameterClosedLoopService service(InMemoryStore store, McpToolRegistry registry,
                                                       McpParameterExtractor extractor,
                                                       HuaweiMallRuntimeArguments runtime,
                                                       ContextFormatter formatter) {
        var descriptors = new ParameterDescriptorRegistry();
        return new MissingParameterClosedLoopService(store, new CancelMatcher(),
                new ToolParameterContractRegistry(), new MissingParameterGate(),
                new ClarificationPromptBuilder(descriptors), registry, extractor,
                new ExactBusinessParameterFilter(), runtime, formatter);
    }

    private Tool tool(String name, Map<String, Object> properties, List<String> required) {
        return Tool.builder().name(name).description(name)
                .inputSchema(new JsonSchema("object", properties, required, null, null, null)).build();
    }

    private CallToolResult ok() {
        return CallToolResult.builder().content(List.of(new TextContent("{\"status\":\"OK\"}")))
                .isError(false).build();
    }

    private static final class InMemoryStore implements PendingClarificationStore {
        private Optional<PendingClarificationState> state = Optional.empty();
        @Override public Optional<PendingClarificationState> get(String userId, String conversationId) { return state; }
        @Override public void saveWithTtl(PendingClarificationState value) { state = Optional.of(value); }
        @Override public Optional<PendingClarificationState> take(String userId, String conversationId) {
            Optional<PendingClarificationState> current = state; state = Optional.empty(); return current;
        }
        @Override public void cancel(String userId, String conversationId) { state = Optional.empty(); }
    }
}
