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

import lombok.Data;

import java.util.List;
import java.util.Map;

/** Fixed, intentionally small assertion vocabulary for Golden V0 traces. */
@Data
public class GoldenCaseExpectation {

    private String expectedRoutingDecision;
    private String expectedRoutingReason;
    private List<String> expectedIntent;
    private List<String> expectedResource;
    private String expectedResolutionStatus;
    private Map<String, List<String>> expectedMissingParameters;
    private List<String> expectedTools;
    private List<String> forbiddenTools;
    private List<String> expectedToolSequence;
    private List<List<String>> expectedToolPlan;
    private List<Map<String, String>> expectedToolBindings;
    private Map<String, Integer> expectedToolInvocationCount;
    private Map<String, Map<String, String>> expectedParameterSource;
    private Map<String, String> expectedTransportStatus;
    private Map<String, String> expectedBusinessStatus;
    private String expectedTaskStatus;
    private String expectedClarificationAction;
    private Boolean expectedPendingTaskIdPresent;
    private String expectedPendingTaskId;
    private String expectedEntityResolutionStatus;
    private String expectedEntityTarget;
    private Integer expectedCandidateCount;
    private Boolean expectedSelectedCandidateIdPresent;
    private Integer expectedEvidenceMinCount;
    private Integer expectedSelectedEvidenceMinCount;
    private List<String> expectedEvidenceChunkIds;
    private Boolean citationApplicable;
    private Integer minValidCitationCount;
    private String expectedFinalCitationStatus;
    private List<String> forbiddenCitationIds;
    private Boolean requireNoInvalidCitationVisible;
    private List<String> expectedValidCitationIds;
    private Boolean requireGroundingEvaluationComplete;
    private Integer maxUnsupportedClaimCount;
    private Integer maxContradictedClaimCount;
    private Integer maxPartiallySupportedClaimCount;
    private Double minCitationCoverage;
    private Double minCitationSupportRate;
    private Double minGroundedClaimRate;
    private Integer maxAuthorityViolationCount;
    private Integer maxUnassociatedCitationCount;
    private String failureStage;
}
