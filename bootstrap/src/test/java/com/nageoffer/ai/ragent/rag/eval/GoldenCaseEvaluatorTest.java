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

package com.nageoffer.ai.ragent.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceDetailVO;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceNodeVO;
import com.nageoffer.ai.ragent.rag.service.RagTraceQueryService;
import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoldenCaseEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RagTraceQueryService traceQueryService = mock(RagTraceQueryService.class);
    private final GoldenCaseEvaluator evaluator = new GoldenCaseEvaluator(traceQueryService, objectMapper);

    @Test
    void reportsMissingParameterAndForbiddenToolDirectly() throws Exception {
        ExecutionRecord record = new ExecutionRecord();
        record.addRoute(route("WARRANTY_STATUS", "MCP", "getWarrantyStatus"));
        Map<String, ExecutionRecord.ResolvedParameterRecord> resolved = new LinkedHashMap<>();
        resolved.put("principalId", parameter("U001", ExecutionRecord.ParameterSource.AUTH_CONTEXT));
        record.addToolObservation(
                ExecutionRecord.ParameterResolutionRecord.builder()
                        .tool("getWarrantyStatus")
                        .required(List.of("sn"))
                        .resolved(resolved)
                        .missing(List.of("sn"))
                        .status(ExecutionRecord.ResolutionStatus.MISSING)
                        .build(),
                ExecutionRecord.ToolExecutionRecord.builder()
                        .tool("getWarrantyStatus")
                        .status(ExecutionRecord.ToolStatus.ERROR)
                        .resultStatus("VALIDATION_ERROR")
                        .build());
        stubTrace("trace-miss", record);

        GoldenCaseExpectation expected = new GoldenCaseExpectation();
        expected.setExpectedIntent(List.of("WARRANTY_STATUS"));
        expected.setExpectedResource(List.of("MCP:getWarrantyStatus"));
        expected.setExpectedResolutionStatus("MISSING");
        expected.setExpectedMissingParameters(Map.of("getWarrantyStatus", List.of("sn")));
        expected.setExpectedTools(List.of());
        expected.setForbiddenTools(List.of("getWarrantyStatus"));
        expected.setExpectedToolSequence(List.of());
        expected.setFailureStage("MISSING_PARAMETER_HANDLING");

        GoldenEvaluationResult result = evaluator.evaluate(request("MISS-02", "trace-miss", expected));

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getFailureStage()).isEqualTo("MISSING_PARAMETER_HANDLING");
        assertThat(assertion(result, "expectedResolutionStatus").isPassed()).isTrue();
        assertThat(assertion(result, "expectedMissingParameters").isPassed()).isTrue();
        assertThat(assertion(result, "forbiddenTools").isPassed()).isFalse();
    }

    @Test
    void validatesClarificationLifecycleAndPendingIdentity() throws Exception {
        ExecutionRecord record = new ExecutionRecord();
        record.setTaskStatus(ExecutionRecord.TaskStatus.NEED_CLARIFICATION);
        record.setPendingTaskId("PT-001");
        record.setClarificationAction(ExecutionRecord.ClarificationAction.CREATED);
        stubTrace("trace-clarification", record);

        GoldenCaseExpectation expected = new GoldenCaseExpectation();
        expected.setExpectedTaskStatus("NEED_CLARIFICATION");
        expected.setExpectedClarificationAction("CREATED");
        expected.setExpectedPendingTaskIdPresent(true);
        expected.setExpectedPendingTaskId("PT-001");

        GoldenEvaluationResult result = evaluator.evaluate(
                request("MISS-02-A", "trace-clarification", expected));

        assertThat(result.isPassed()).isTrue();
        assertThat(assertion(result, "expectedTaskStatus").isPassed()).isTrue();
        assertThat(assertion(result, "expectedClarificationAction").isPassed()).isTrue();
        assertThat(assertion(result, "expectedPendingTaskIdPresent").isPassed()).isTrue();
        assertThat(assertion(result, "expectedPendingTaskId").isPassed()).isTrue();
    }

    @Test
    void detectsPendingIdentityMismatchAcrossRoundsWhenExpectedIdIsProvided() throws Exception {
        ExecutionRecord record = new ExecutionRecord();
        record.setTaskStatus(ExecutionRecord.TaskStatus.COMPLETED);
        record.setPendingTaskId("PT-OTHER");
        record.setClarificationAction(ExecutionRecord.ClarificationAction.RESUMED_AND_CLEARED);
        stubTrace("trace-resume", record);

        GoldenCaseExpectation expected = new GoldenCaseExpectation();
        expected.setExpectedTaskStatus("COMPLETED");
        expected.setExpectedClarificationAction("RESUMED_AND_CLEARED");
        expected.setExpectedPendingTaskId("PT-001");

        GoldenEvaluationResult result = evaluator.evaluate(
                request("MISS-02-B", "trace-resume", expected));

        assertThat(result.isPassed()).isFalse();
        assertThat(assertion(result, "expectedPendingTaskId").isPassed()).isFalse();
    }

    @Test
    void validatesDependencySequenceAndToolOutputBinding() throws Exception {
        ExecutionRecord record = new ExecutionRecord();
        record.addRoute(route("WARRANTY_STATUS", "MCP", "getWarrantyStatus"));
        record.addToolObservation(null,
                ExecutionRecord.ToolExecutionRecord.builder()
                        .tool("getWarrantyStatus")
                        .status(ExecutionRecord.ToolStatus.SUCCESS)
                        .resultStatus("NOT_FOUND")
                        .build());
        Map<String, ExecutionRecord.ResolvedParameterRecord> resolved = new LinkedHashMap<>();
        resolved.put("sn", parameter("上个月买的手表SN", ExecutionRecord.ParameterSource.USER_QUERY));
        record.getParameterResolutions().add(ExecutionRecord.ParameterResolutionRecord.builder()
                .tool("getWarrantyStatus")
                .required(List.of("sn"))
                .resolved(resolved)
                .missing(List.of())
                .status(ExecutionRecord.ResolutionStatus.RESOLVED)
                .build());
        stubTrace("trace-dep", record);

        GoldenCaseExpectation expected = new GoldenCaseExpectation();
        expected.setExpectedIntent(List.of("WARRANTY_STATUS"));
        expected.setExpectedResource(List.of("MCP:getWarrantyStatus"));
        expected.setExpectedTools(List.of("getOrder", "getWarrantyStatus"));
        expected.setExpectedToolSequence(List.of("getOrder", "getWarrantyStatus"));
        expected.setExpectedParameterSource(Map.of(
                "getWarrantyStatus", Map.of("sn", "TOOL_OUTPUT")));
        expected.setExpectedTransportStatus(Map.of("getWarrantyStatus", "SUCCESS"));
        expected.setExpectedBusinessStatus(Map.of("getWarrantyStatus", "OK"));
        expected.setFailureStage("DEPENDENCY_EXECUTION");

        GoldenEvaluationResult result = evaluator.evaluate(request("DEP-01", "trace-dep", expected));

        assertThat(result.isPassed()).isFalse();
        assertThat(assertion(result, "expectedToolSequence").isPassed()).isFalse();
        assertThat(assertion(result, "expectedParameterSource").isPassed()).isFalse();
        assertThat(assertion(result, "expectedTransportStatus").isPassed()).isTrue();
        assertThat(assertion(result, "expectedBusinessStatus").isPassed()).isFalse();
    }

    private void stubTrace(String traceId, ExecutionRecord record) throws Exception {
        String json = objectMapper.writeValueAsString(record);
        RagTraceNodeVO node = RagTraceNodeVO.builder()
                .nodeName("execution-record")
                .extraData(json)
                .build();
        when(traceQueryService.detail(traceId)).thenReturn(RagTraceDetailVO.builder()
                .nodes(List.of(node))
                .build());
    }

    private GoldenEvaluationRequest request(String caseId,
                                            String traceId,
                                            GoldenCaseExpectation expected) {
        GoldenEvaluationRequest request = new GoldenEvaluationRequest();
        request.setCaseId(caseId);
        request.setTraceId(traceId);
        request.setExpected(expected);
        return request;
    }

    private ExecutionRecord.RouteRecord route(String intent, String type, String name) {
        return ExecutionRecord.RouteRecord.builder()
                .intent(intent)
                .resource(ExecutionRecord.ResourceRecord.builder().type(type).name(name).build())
                .build();
    }

    private ExecutionRecord.ResolvedParameterRecord parameter(String value,
                                                               ExecutionRecord.ParameterSource source) {
        return ExecutionRecord.ResolvedParameterRecord.builder().value(value).source(source).build();
    }

    private GoldenAssertionResult assertion(GoldenEvaluationResult result, String name) {
        return result.getAssertions().stream()
                .filter(item -> name.equals(item.getAssertion()))
                .findFirst()
                .orElseThrow();
    }
}
