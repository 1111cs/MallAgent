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

import com.nageoffer.ai.ragent.rag.trace.ExecutionRecord;
import org.springframework.stereotype.Component;

import java.util.List;

/** Maps evidence lifecycle metadata into the structured trace without storing chunk content. */
@Component
public class EvidenceTraceMapper {

    public List<ExecutionRecord.EvidenceTraceRecord> map(List<EvidenceRecord> evidence) {
        if (evidence == null) return List.of();
        return evidence.stream().map(item -> ExecutionRecord.EvidenceTraceRecord.builder()
                .evidenceId(item.getEvidenceId())
                .promptOrder(item.getPromptOrder())
                .routingDecision(item.getRoutingDecision())
                .routeId(item.getRouteId())
                .intentId(item.getIntentId())
                .retrievalChannel(item.getRetrievalChannel())
                .kbId(item.getKbId())
                .kbName(item.getKbName())
                .collectionName(item.getCollectionName())
                .documentId(item.getDocumentId())
                .documentTitle(item.getDocumentTitle())
                .chunkId(item.getChunkId())
                .chunkIndex(item.getChunkIndex())
                .retrievalScore(item.getRetrievalScore())
                .retrievalRank(item.getRetrievalRank())
                .rerankScore(item.getRerankScore())
                .finalRank(item.getFinalRank())
                .selectedForPrompt(item.isSelectedForPrompt())
                .provenanceStatus(item.getProvenanceStatus() == null ? null : item.getProvenanceStatus().name())
                .contentHash(item.getContentHash())
                .build()).toList();
    }
}
