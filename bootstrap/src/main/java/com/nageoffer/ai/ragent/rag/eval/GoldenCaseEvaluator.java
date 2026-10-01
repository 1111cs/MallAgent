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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceDetailVO;
import com.nageoffer.ai.ragent.rag.controller.vo.RagTraceNodeVO;
import com.nageoffer.ai.ragent.rag.service.RagTraceQueryService;
import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Evaluates one frozen Golden expectation against one real execution-record trace node. */
@Service
@RequiredArgsConstructor
public class GoldenCaseEvaluator {

    private static final String EXECUTION_RECORD_NODE = "execution-record";

    private final RagTraceQueryService traceQueryService;
    private final ObjectMapper objectMapper;

    public GoldenEvaluationResult evaluate(GoldenEvaluationRequest request) {
        List<GoldenAssertionResult> assertions = new ArrayList<>();
        RagTraceDetailVO detail = request == null ? null : traceQueryService.detail(request.getTraceId());
        add(assertions, "traceExists", true, detail != null);

        RagTraceNodeVO node = findExecutionRecord(detail);
        add(assertions, "executionRecordExists", true, node != null);
        ExecutionRecord actual = parse(node, assertions);
        GoldenCaseExpectation expected = request == null ? null : request.getExpected();
        if (actual != null && expected != null) {
            evaluateExpected(expected, actual, assertions);
        }

        boolean passed = assertions.stream().allMatch(GoldenAssertionResult::isPassed);
        return GoldenEvaluationResult.builder()
                .caseId(request == null ? null : request.getCaseId())
                .traceId(request == null ? null : request.getTraceId())
                .passed(passed)
                .failureStage(passed ? "NONE" : resolveFailureStage(expected, assertions))
                .assertions(assertions)
                .actual(actual)
                .build();
    }

    private RagTraceNodeVO findExecutionRecord(RagTraceDetailVO detail) {
        if (detail == null || detail.getNodes() == null) {
            return null;
        }
        RagTraceNodeVO selected = null;
        for (RagTraceNodeVO node : detail.getNodes()) {
            if (node != null && EXECUTION_RECORD_NODE.equals(node.getNodeName())
                    && node.getExtraData() != null && !node.getExtraData().isBlank()) {
                selected = node;
            }
        }
        return selected;
    }

    private ExecutionRecord parse(RagTraceNodeVO node, List<GoldenAssertionResult> assertions) {
        if (node == null) {
            return null;
        }
        try {
            ExecutionRecord record = objectMapper.readerFor(ExecutionRecord.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(node.getExtraData());
            add(assertions, "executionRecordParseable", true, true);
            return record;
        } catch (Exception ex) {
            add(assertions, "executionRecordParseable", true, false);
            return null;
        }
    }

    private void evaluateExpected(GoldenCaseExpectation expected,
                                  ExecutionRecord actual,
                                  List<GoldenAssertionResult> assertions) {
        if (expected.getExpectedRoutingDecision() != null) {
            add(assertions, "expectedRoutingDecision", expected.getExpectedRoutingDecision(),
                    actual.getRoutingDecision() == null ? null : actual.getRoutingDecision().name());
        }
        if (expected.getExpectedRoutingReason() != null) {
            add(assertions, "expectedRoutingReason", expected.getExpectedRoutingReason(),
                    actual.getRoutingReason() == null ? null : actual.getRoutingReason().name());
        }
        List<String> intents = actual.getRoutes().stream()
                .map(ExecutionRecord.RouteRecord::getIntent)
                .filter(Objects::nonNull)
                .toList();
        List<String> resources = actual.getRoutes().stream()
                .map(ExecutionRecord.RouteRecord::getResource)
                .filter(Objects::nonNull)
                .map(resource -> resource.getType() + ":" + resource.getName())
                .toList();
        List<String> toolSequence = actual.getToolExecutions().stream()
                .map(ExecutionRecord.ToolExecutionRecord::getTool)
                .toList();

        assertSet(assertions, "expectedIntent", expected.getExpectedIntent(), intents);
        assertSet(assertions, "expectedResource", expected.getExpectedResource(), resources);
        assertSet(assertions, "expectedTools", expected.getExpectedTools(), distinct(toolSequence));
        assertForbidden(assertions, expected.getForbiddenTools(), toolSequence);
        assertList(assertions, "expectedToolSequence", expected.getExpectedToolSequence(), toolSequence);
        assertToolPlan(assertions, expected.getExpectedToolPlan(), actual.getToolPlan());
        assertToolBindings(assertions, expected.getExpectedToolBindings(), actual.getToolBindings());
        assertToolInvocationCount(assertions, expected.getExpectedToolInvocationCount(), toolSequence);

        Map<String, ExecutionRecord.ParameterResolutionRecord> resolutions = actual.getParameterResolutions().stream()
                .filter(item -> item.getTool() != null)
                .collect(Collectors.toMap(
                        ExecutionRecord.ParameterResolutionRecord::getTool,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new));
        Map<String, ExecutionRecord.ToolExecutionRecord> executions = actual.getToolExecutions().stream()
                .filter(item -> item.getTool() != null)
                .collect(Collectors.toMap(
                        ExecutionRecord.ToolExecutionRecord::getTool,
                        Function.identity(),
                        (left, right) -> right,
                        LinkedHashMap::new));

        assertResolutionStatus(assertions, expected, resolutions);
        assertMissing(assertions, expected.getExpectedMissingParameters(), resolutions);
        assertParameterSources(assertions, expected.getExpectedParameterSource(), resolutions);
        assertExecutionStatus(assertions, "expectedTransportStatus", expected.getExpectedTransportStatus(),
                executions, ExecutionRecord.ToolExecutionRecord::getStatus);
        assertExecutionStatus(assertions, "expectedBusinessStatus", expected.getExpectedBusinessStatus(),
                executions, ExecutionRecord.ToolExecutionRecord::getResultStatus);
        assertLifecycle(assertions, expected, actual);
        assertEntityResolution(assertions, expected, actual);
        assertEvidence(assertions, expected, actual);
        assertCitation(assertions, expected, actual);
        assertGrounding(assertions, expected, actual);
    }

    private void assertGrounding(List<GoldenAssertionResult> assertions,
                                 GoldenCaseExpectation expected,
                                 ExecutionRecord actual) {
        var grounding = actual.getGrounding();
        if (expected.getRequireGroundingEvaluationComplete() != null) {
            boolean complete = grounding != null && grounding.getEvaluationStatus()
                    == com.nageoffer.ai.ragent.rag.core.grounding.GroundingEvaluationRecord.EvaluationStatus.COMPLETE;
            add(assertions, "requireGroundingEvaluationComplete", expected.getRequireGroundingEvaluationComplete(), complete);
        }
        if (grounding == null) return;
        assertMaximum(assertions, "maxUnsupportedClaimCount", expected.getMaxUnsupportedClaimCount(), grounding.getUnsupportedClaimCount());
        assertMaximum(assertions, "maxContradictedClaimCount", expected.getMaxContradictedClaimCount(), grounding.getContradictedClaimCount());
        assertMaximum(assertions, "maxPartiallySupportedClaimCount", expected.getMaxPartiallySupportedClaimCount(), grounding.getPartiallySupportedClaimCount());
        assertMinimum(assertions, "minCitationCoverage", expected.getMinCitationCoverage(), grounding.getCitationCoverage());
        assertMinimum(assertions, "minCitationSupportRate", expected.getMinCitationSupportRate(), grounding.getCitationSupportRate());
        assertMinimum(assertions, "minGroundedClaimRate", expected.getMinGroundedClaimRate(), grounding.getGroundedClaimRate());
        assertMaximum(assertions, "maxAuthorityViolationCount", expected.getMaxAuthorityViolationCount(), grounding.getAuthorityViolationCount());
        assertMaximum(assertions, "maxUnassociatedCitationCount", expected.getMaxUnassociatedCitationCount(), grounding.getUnassociatedCitationCount());
    }

    private void assertMaximum(List<GoldenAssertionResult> assertions, String name, Integer expected, int actual) {
        if (expected != null) add(assertions, name, expected, actual, actual <= expected);
    }

    private void assertMinimum(List<GoldenAssertionResult> assertions, String name, Double expected, Double actual) {
        if (expected != null) add(assertions, name, expected, actual, actual != null && actual >= expected);
    }

    private void assertCitation(List<GoldenAssertionResult> assertions,
                                GoldenCaseExpectation expected,
                                ExecutionRecord actual) {
        ExecutionRecord.CitationTraceRecord citation = actual.getCitation();
        if (expected.getCitationApplicable() != null) {
            boolean applicable = citation != null && !"NOT_APPLICABLE".equals(citation.getFinalStatus());
            add(assertions, "citationApplicable", expected.getCitationApplicable(), applicable);
        }
        if (expected.getExpectedFinalCitationStatus() != null) {
            add(assertions, "expectedFinalCitationStatus", expected.getExpectedFinalCitationStatus(),
                    citation == null ? null : citation.getFinalStatus());
        }
        List<String> valid = citation == null || citation.getValidEvidenceIds() == null
                ? List.of() : citation.getValidEvidenceIds();
        List<String> invalid = citation == null || citation.getInvalidEvidenceIds() == null
                ? List.of() : citation.getInvalidEvidenceIds();
        if (expected.getMinValidCitationCount() != null) {
            add(assertions, "minValidCitationCount", expected.getMinValidCitationCount(), valid.size(),
                    valid.size() >= expected.getMinValidCitationCount());
        }
        if (expected.getExpectedValidCitationIds() != null) {
            add(assertions, "expectedValidCitationIds", expected.getExpectedValidCitationIds(), valid,
                    valid.containsAll(expected.getExpectedValidCitationIds()));
        }
        if (expected.getForbiddenCitationIds() != null) {
            List<String> observedForbidden = expected.getForbiddenCitationIds().stream()
                    .filter(valid::contains).toList();
            add(assertions, "forbiddenCitationIds", List.of(), observedForbidden, observedForbidden.isEmpty());
        }
        if (Boolean.TRUE.equals(expected.getRequireNoInvalidCitationVisible())) {
            boolean safelyDelivered = invalid.isEmpty() || citation != null && citation.isCleanupApplied();
            add(assertions, "requireNoInvalidCitationVisible", true, safelyDelivered);
        }
    }

    private void assertEvidence(List<GoldenAssertionResult> assertions,
                                GoldenCaseExpectation expected,
                                ExecutionRecord actual) {
        List<ExecutionRecord.EvidenceTraceRecord> evidence = actual.getRetrievedEvidence() == null
                ? List.of() : actual.getRetrievedEvidence();
        if (expected.getExpectedEvidenceMinCount() != null) {
            add(assertions, "expectedEvidenceMinCount", expected.getExpectedEvidenceMinCount(), evidence.size(),
                    evidence.size() >= expected.getExpectedEvidenceMinCount());
        }
        long selected = evidence.stream().filter(ExecutionRecord.EvidenceTraceRecord::isSelectedForPrompt).count();
        if (expected.getExpectedSelectedEvidenceMinCount() != null) {
            add(assertions, "expectedSelectedEvidenceMinCount", expected.getExpectedSelectedEvidenceMinCount(), selected,
                    selected >= expected.getExpectedSelectedEvidenceMinCount());
        }
        if (expected.getExpectedEvidenceChunkIds() != null) {
            List<String> chunks = evidence.stream().map(ExecutionRecord.EvidenceTraceRecord::getChunkId)
                    .filter(Objects::nonNull).distinct().toList();
            add(assertions, "expectedEvidenceChunkIds", expected.getExpectedEvidenceChunkIds(), chunks,
                    chunks.containsAll(expected.getExpectedEvidenceChunkIds()));
        }
    }

    private void assertToolPlan(List<GoldenAssertionResult> assertions,
                                List<List<String>> expected,
                                List<ExecutionRecord.ToolPlanStep> actual) {
        if (expected == null) {
            return;
        }
        List<List<String>> observed = actual.stream()
                .map(ExecutionRecord.ToolPlanStep::getTools)
                .toList();
        add(assertions, "expectedToolPlan", expected, observed, expected.equals(observed));
    }

    private void assertToolBindings(List<GoldenAssertionResult> assertions,
                                    List<Map<String, String>> expected,
                                    List<ExecutionRecord.ToolBindingRecord> actual) {
        if (expected == null) {
            return;
        }
        List<Map<String, String>> observed = actual.stream().map(binding -> {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("fromTool", binding.getFromTool());
            value.put("fromField", binding.getFromField());
            value.put("toTool", binding.getToTool());
            value.put("toParameter", binding.getToParameter());
            value.put("source", binding.getSource() == null ? null : binding.getSource().name());
            return value;
        }).toList();
        add(assertions, "expectedToolBindings", expected, observed, expected.equals(observed));
    }

    private void assertToolInvocationCount(List<GoldenAssertionResult> assertions,
                                           Map<String, Integer> expected,
                                           List<String> sequence) {
        if (expected == null) {
            return;
        }
        Map<String, Integer> observed = new LinkedHashMap<>();
        expected.keySet().forEach(tool -> observed.put(tool,
                (int) sequence.stream().filter(tool::equals).count()));
        add(assertions, "expectedToolInvocationCount", expected, observed, expected.equals(observed));
    }

    private void assertEntityResolution(List<GoldenAssertionResult> assertions,
                                        GoldenCaseExpectation expected,
                                        ExecutionRecord actual) {
        ExecutionRecord.EntityResolutionRecord entity = actual.getEntityResolution();
        if (expected.getExpectedEntityResolutionStatus() != null) {
            add(assertions, "expectedEntityResolutionStatus", expected.getExpectedEntityResolutionStatus(),
                    entity == null ? null : entity.getStatus());
        }
        if (expected.getExpectedEntityTarget() != null) {
            add(assertions, "expectedEntityTarget", expected.getExpectedEntityTarget(),
                    entity == null ? null : entity.getTarget());
        }
        if (expected.getExpectedCandidateCount() != null) {
            add(assertions, "expectedCandidateCount", expected.getExpectedCandidateCount(),
                    entity == null ? null : entity.getCandidateCount());
        }
        if (expected.getExpectedSelectedCandidateIdPresent() != null) {
            boolean present = entity != null && entity.getSelectedCandidateId() != null
                    && !entity.getSelectedCandidateId().isBlank();
            add(assertions, "expectedSelectedCandidateIdPresent",
                    expected.getExpectedSelectedCandidateIdPresent(), present);
        }
    }

    private void assertLifecycle(List<GoldenAssertionResult> assertions,
                                 GoldenCaseExpectation expected,
                                 ExecutionRecord actual) {
        if (expected.getExpectedTaskStatus() != null) {
            String observed = actual.getTaskStatus() == null ? null : actual.getTaskStatus().name();
            add(assertions, "expectedTaskStatus", expected.getExpectedTaskStatus(), observed);
        }
        if (expected.getExpectedClarificationAction() != null) {
            String observed = actual.getClarificationAction() == null
                    ? null : actual.getClarificationAction().name();
            add(assertions, "expectedClarificationAction",
                    expected.getExpectedClarificationAction(), observed);
        }
        if (expected.getExpectedPendingTaskIdPresent() != null) {
            boolean observed = actual.getPendingTaskId() != null && !actual.getPendingTaskId().isBlank();
            add(assertions, "expectedPendingTaskIdPresent",
                    expected.getExpectedPendingTaskIdPresent(), observed);
        }
        if (expected.getExpectedPendingTaskId() != null) {
            add(assertions, "expectedPendingTaskId",
                    expected.getExpectedPendingTaskId(), actual.getPendingTaskId());
        }
    }

    private void assertResolutionStatus(List<GoldenAssertionResult> assertions,
                                        GoldenCaseExpectation expected,
                                        Map<String, ExecutionRecord.ParameterResolutionRecord> resolutions) {
        if (expected.getExpectedResolutionStatus() == null) {
            return;
        }
        List<String> targetTools = expected.getExpectedTools();
        if (targetTools == null || targetTools.isEmpty()) {
            targetTools = expected.getForbiddenTools();
        }
        Map<String, String> observed = new LinkedHashMap<>();
        if (targetTools != null) {
            for (String tool : targetTools) {
                ExecutionRecord.ParameterResolutionRecord resolution = resolutions.get(tool);
                observed.put(tool, resolution == null || resolution.getStatus() == null
                        ? null : resolution.getStatus().name());
            }
        }
        boolean passed = !observed.isEmpty()
                && observed.values().stream().allMatch(expected.getExpectedResolutionStatus()::equals);
        add(assertions, "expectedResolutionStatus", expected.getExpectedResolutionStatus(), observed, passed);
    }

    private void assertMissing(List<GoldenAssertionResult> assertions,
                               Map<String, List<String>> expected,
                               Map<String, ExecutionRecord.ParameterResolutionRecord> resolutions) {
        if (expected == null) {
            return;
        }
        Map<String, List<String>> actual = new LinkedHashMap<>();
        boolean passed = true;
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            ExecutionRecord.ParameterResolutionRecord resolution = resolutions.get(entry.getKey());
            List<String> missing = resolution == null || resolution.getMissing() == null
                    ? Collections.emptyList() : resolution.getMissing();
            actual.put(entry.getKey(), missing);
            passed &= new LinkedHashSet<>(missing).containsAll(entry.getValue());
        }
        add(assertions, "expectedMissingParameters", expected, actual, passed);
    }

    private void assertParameterSources(List<GoldenAssertionResult> assertions,
                                        Map<String, Map<String, String>> expected,
                                        Map<String, ExecutionRecord.ParameterResolutionRecord> resolutions) {
        if (expected == null) {
            return;
        }
        Map<String, Map<String, String>> actual = new LinkedHashMap<>();
        boolean passed = true;
        for (Map.Entry<String, Map<String, String>> toolEntry : expected.entrySet()) {
            Map<String, String> observedSources = new LinkedHashMap<>();
            ExecutionRecord.ParameterResolutionRecord resolution = resolutions.get(toolEntry.getKey());
            for (Map.Entry<String, String> parameter : toolEntry.getValue().entrySet()) {
                String source = null;
                if (resolution != null && resolution.getResolved() != null) {
                    ExecutionRecord.ResolvedParameterRecord resolved = resolution.getResolved().get(parameter.getKey());
                    source = resolved == null || resolved.getSource() == null ? null : resolved.getSource().name();
                }
                observedSources.put(parameter.getKey(), source);
                passed &= Objects.equals(parameter.getValue(), source);
            }
            actual.put(toolEntry.getKey(), observedSources);
        }
        add(assertions, "expectedParameterSource", expected, actual, passed);
    }

    private <T> void assertExecutionStatus(List<GoldenAssertionResult> assertions,
                                           String assertion,
                                           Map<String, String> expected,
                                           Map<String, ExecutionRecord.ToolExecutionRecord> executions,
                                           Function<ExecutionRecord.ToolExecutionRecord, T> extractor) {
        if (expected == null) {
            return;
        }
        Map<String, String> actual = new LinkedHashMap<>();
        boolean passed = true;
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            ExecutionRecord.ToolExecutionRecord execution = executions.get(entry.getKey());
            T value = execution == null ? null : extractor.apply(execution);
            String observed = value == null ? null : value.toString();
            actual.put(entry.getKey(), observed);
            passed &= Objects.equals(entry.getValue(), observed);
        }
        add(assertions, assertion, expected, actual, passed);
    }

    private void assertForbidden(List<GoldenAssertionResult> assertions,
                                 List<String> forbidden,
                                 List<String> actualTools) {
        if (forbidden == null) {
            return;
        }
        List<String> invoked = forbidden.stream().filter(actualTools::contains).toList();
        add(assertions, "forbiddenTools", Collections.emptyList(), invoked, invoked.isEmpty());
    }

    private void assertSet(List<GoldenAssertionResult> assertions,
                           String assertion,
                           List<String> expected,
                           List<String> actual) {
        if (expected == null) {
            return;
        }
        add(assertions, assertion, expected, actual,
                new LinkedHashSet<>(expected).equals(new LinkedHashSet<>(actual)));
    }

    private void assertList(List<GoldenAssertionResult> assertions,
                            String assertion,
                            List<String> expected,
                            List<String> actual) {
        if (expected != null) {
            add(assertions, assertion, expected, actual, expected.equals(actual));
        }
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String resolveFailureStage(GoldenCaseExpectation expected,
                                       List<GoldenAssertionResult> assertions) {
        boolean infrastructureFailure = assertions.stream()
                .filter(item -> item.getAssertion().startsWith("trace")
                        || item.getAssertion().startsWith("executionRecord"))
                .anyMatch(item -> !item.isPassed());
        if (infrastructureFailure) {
            return "OBSERVABILITY";
        }
        return expected == null || expected.getFailureStage() == null
                ? "GOLDEN_ASSERTION" : expected.getFailureStage();
    }

    private void add(List<GoldenAssertionResult> assertions,
                     String assertion,
                     Object expected,
                     Object actual) {
        add(assertions, assertion, expected, actual, Objects.equals(expected, actual));
    }

    private void add(List<GoldenAssertionResult> assertions,
                     String assertion,
                     Object expected,
                     Object actual,
                     boolean passed) {
        assertions.add(GoldenAssertionResult.builder()
                .assertion(assertion)
                .passed(passed)
                .expected(expected)
                .actual(actual)
                .build());
    }
}
