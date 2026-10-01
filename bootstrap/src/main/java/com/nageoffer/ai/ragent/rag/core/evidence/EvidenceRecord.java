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

package com.nageoffer.ai.ragent.rag.core.evidence;

import com.nageoffer.ai.ragent.rag.core.routing.RoutingDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EvidenceRecord {
    private String evidenceId;
    private Integer promptOrder;
    private RoutingDecision routingDecision;
    private String routeId;
    private String subQuestion;
    private String intentId;
    private String retrievalChannel;
    private String kbId;
    private String kbName;
    private String collectionName;
    private String documentId;
    private String documentTitle;
    private String sourceType;
    private String sourceLocation;
    private String chunkId;
    private Integer chunkIndex;
    private String section;
    private Double retrievalScore;
    private Integer retrievalRank;
    private Double rerankScore;
    private Integer finalRank;
    private boolean selectedForPrompt;
    @Builder.Default private ProvenanceStatus provenanceStatus = ProvenanceStatus.PARTIAL;
    private String content;
    private String contentHash;

    public String originKey() {
        String origin = routeId == null ? routingDecision + ":" + collectionName : routeId;
        return origin + ":" + collectionName + ":" + retrievalChannel;
    }

    /** Candidate-set scope for final rerank order. */
    public String rankingScopeKey() {
        if (routeId != null && !routeId.isBlank()) return routeId;
        if (intentId != null && !intentId.isBlank()) return "intent:" + intentId;
        return "routing:" + routingDecision;
    }
}
