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

package com.nageoffer.ai.ragent.rag.trace;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingDecision;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingReason;
import com.nageoffer.ai.ragent.rag.core.grounding.GroundingEvaluationRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Request-scoped structured record for routing, parameter resolution and tool execution. */
@Data
public class ExecutionRecord {

    private final List<RouteRecord> routes = new ArrayList<>();
    private final List<ParameterResolutionRecord> parameterResolutions = new ArrayList<>();
    private final List<ToolExecutionRecord> toolExecutions = new ArrayList<>();
    private RoutingDecision routingDecision;
    private RoutingReason routingReason;
    private TaskStatus taskStatus;
    private String pendingTaskId;
    private ClarificationAction clarificationAction;
    private EntityResolutionRecord entityResolution;
    private final List<ToolPlanStep> toolPlan = new ArrayList<>();
    private final List<ToolBindingRecord> toolBindings = new ArrayList<>();
    private List<EvidenceTraceRecord> retrievedEvidence = new ArrayList<>();
    private CitationTraceRecord citation;
    private GroundingEvaluationRecord grounding;
    @JsonIgnore
    private transient String directResponse;

    public void addRoute(RouteRecord route) {
        if (route != null) {
            routes.add(route);
        }
    }

    public synchronized void addToolObservation(ParameterResolutionRecord resolution,
                                                ToolExecutionRecord execution) {
        if (resolution != null) {
            parameterResolutions.add(resolution);
        }
        if (execution != null) {
            if (execution.getSequence() == null) {
                execution.setSequence(toolExecutions.size() + 1);
            }
            toolExecutions.add(execution);
        }
    }

    public synchronized void addToolBinding(ToolBindingRecord binding) {
        if (binding != null) toolBindings.add(binding);
    }

    public boolean isEmpty() {
        return routes.isEmpty() && parameterResolutions.isEmpty() && toolExecutions.isEmpty()
                && toolPlan.isEmpty() && toolBindings.isEmpty() && retrievedEvidence.isEmpty()
                && citation == null && grounding == null && routingDecision == null && taskStatus == null;
    }

    public enum ParameterSource {
        USER_QUERY, CLARIFICATION_INPUT, AUTH_CONTEXT, ENTITY_RESOLUTION, TOOL_OUTPUT, DEFAULT
    }

    public enum ResolutionStatus {
        RESOLVED, MISSING, AMBIGUOUS
    }

    public enum ToolStatus {
        SUCCESS, ERROR, NOT_EXECUTED
    }

    public enum TaskStatus {
        NEED_CLARIFICATION, NEED_SELECTION, READY, COMPLETED, TERMINATED, CANCELLED
    }

    public enum ClarificationAction {
        CREATED, UPDATED, RESUMED_AND_CLEARED, CANCELLED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolPlanStep {
        private Integer step;
        @Builder.Default
        private List<String> tools = List.of();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolBindingRecord {
        private String fromTool;
        private String fromField;
        private String toTool;
        private String toParameter;
        private ParameterSource source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityResolutionRecord {
        private String status;
        private String target;
        private Integer candidateCount;
        private String selectedCandidateId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteRecord {
        private String subQuestion;
        private String intent;
        private Double confidence;
        private ResourceRecord resource;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceRecord {
        private String type;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedParameterRecord {
        private Object value;
        private ParameterSource source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterResolutionRecord {
        private String tool;
        @Builder.Default
        private List<String> required = List.of();
        @Builder.Default
        private Map<String, ResolvedParameterRecord> resolved = new LinkedHashMap<>();
        @Builder.Default
        private List<String> missing = List.of();
        private ResolutionStatus status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecutionRecord {
        private Integer sequence;
        private String tool;
        @Builder.Default
        private Map<String, Object> arguments = new LinkedHashMap<>();
        private ToolStatus status;
        private String resultStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceTraceRecord {
        private String evidenceId;
        private Integer promptOrder;
        private RoutingDecision routingDecision;
        private String routeId;
        private String intentId;
        private String retrievalChannel;
        private String kbId;
        private String kbName;
        private String collectionName;
        private String documentId;
        private String documentTitle;
        private String chunkId;
        private Integer chunkIndex;
        private Double retrievalScore;
        private Integer retrievalRank;
        private Double rerankScore;
        private Integer finalRank;
        private boolean selectedForPrompt;
        private String provenanceStatus;
        private String contentHash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationTraceRecord {
        @Builder.Default
        private List<String> usedEvidenceIds = List.of();
        @Builder.Default
        private List<String> validEvidenceIds = List.of();
        @Builder.Default
        private List<String> invalidEvidenceIds = List.of();
        private String initialStatus;
        private String finalStatus;
        private boolean cleanupApplied;
    }
}
