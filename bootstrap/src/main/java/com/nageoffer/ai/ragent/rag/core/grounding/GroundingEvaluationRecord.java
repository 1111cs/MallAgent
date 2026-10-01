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

package com.nageoffer.ai.ragent.rag.core.grounding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroundingEvaluationRecord {
    public enum EvaluationStatus { COMPLETE, EVALUATION_ERROR, NOT_APPLICABLE }
    public enum ClaimCategory {
        POLICY_RULE, PRODUCT_SPEC, SUPPORT_GUIDE, CURRENT_ORDER_OR_LOGISTICS,
        CURRENT_WARRANTY, CURRENT_INVENTORY_OR_PRICE, USER_COUPON_OR_REPAIR_STATE,
        SELF_IDENTITY, OTHER_FACTUAL, NON_FACTUAL
    }
    public enum AuthorityType { KB, TOOL, AUTH_CONTEXT, UNDETERMINED, NONE }
    public enum GroundingStatus { SUPPORTED, PARTIALLY_SUPPORTED, UNSUPPORTED, CONTRADICTED, UNASSESSED }
    public enum CitationAlignmentStatus { ALIGNED, MISALIGNED, MISSING, UNASSOCIATED, NOT_APPLICABLE }
    public enum AuthorityStatus { CORRECT, WRONG_SOURCE, UNDETERMINED, NOT_APPLICABLE }

    private EvaluationStatus evaluationStatus;
    private String judgeModelId;
    private String judgePromptVersion;
    private Instant evaluationTimestamp;
    @Builder.Default private List<ClaimRecord> claims = List.of();
    private int factualClaimCount;
    private int supportedClaimCount;
    private int partiallySupportedClaimCount;
    private int unsupportedClaimCount;
    private int contradictedClaimCount;
    private int kbClaimCount;
    private int locallyCitedKbClaimCount;
    private int alignedCitationClaimCount;
    private int authorityViolationCount;
    private int unassociatedCitationCount;
    private Double citationCoverage;
    private Double citationSupportRate;
    private Double groundedClaimRate;
    private String errorCode;
    private String errorDetail;
    private String judgeOutputSnippet;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimRecord {
        private String claimId;
        private Integer paragraphIndex;
        private Integer sentenceIndex;
        private String claimText;
        private ClaimCategory category;
        private AuthorityType expectedAuthority;
        @Builder.Default private List<String> localCitationIds = List.of();
        @Builder.Default private List<String> supportingEvidenceIds = List.of();
        @Builder.Default private List<String> supportingToolExecutionIds = List.of();
        private GroundingStatus groundingStatus;
        private CitationAlignmentStatus citationAlignmentStatus;
        private AuthorityStatus authorityStatus;
        private String reasonCode;
    }
}
