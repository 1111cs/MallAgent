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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j @Component @RequiredArgsConstructor
public class EvidenceProvenanceEnricher {
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public List<EvidenceRecord> enrich(List<EvidenceRecord> evidence) {
        if (evidence == null || evidence.isEmpty()) return evidence == null ? List.of() : evidence;
        try {
            Set<String> docIds = evidence.stream().map(EvidenceRecord::getDocumentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<String, KnowledgeDocumentDO> documents = docIds.isEmpty() ? Map.of()
                    : documentMapper.selectByIds(docIds).stream()
                    .collect(Collectors.toMap(KnowledgeDocumentDO::getId, Function.identity()));
            Set<String> kbIds = documents.values().stream().map(KnowledgeDocumentDO::getKbId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Set<String> collections = evidence.stream().map(EvidenceRecord::getCollectionName)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            List<KnowledgeBaseDO> bases = knowledgeBaseMapper.selectList(
                    Wrappers.lambdaQuery(KnowledgeBaseDO.class).and(q -> {
                        if (!kbIds.isEmpty()) q.in(KnowledgeBaseDO::getId, kbIds);
                        if (!collections.isEmpty()) {
                            if (!kbIds.isEmpty()) q.or();
                            q.in(KnowledgeBaseDO::getCollectionName, collections);
                        }
                    }));
            Map<String, KnowledgeBaseDO> byId = bases.stream()
                    .collect(Collectors.toMap(KnowledgeBaseDO::getId, Function.identity(), (a, b) -> a));
            Map<String, KnowledgeBaseDO> byCollection = bases.stream()
                    .filter(kb -> kb.getCollectionName() != null)
                    .collect(Collectors.toMap(KnowledgeBaseDO::getCollectionName, Function.identity(), (a, b) -> a));
            for (EvidenceRecord item : evidence) {
                KnowledgeDocumentDO document = documents.get(item.getDocumentId());
                if (document != null) {
                    item.setDocumentTitle(document.getDocName());
                    item.setSourceType(document.getSourceType());
                    item.setSourceLocation(document.getSourceLocation());
                    item.setKbId(document.getKbId());
                }
                KnowledgeBaseDO base = item.getKbId() == null ? byCollection.get(item.getCollectionName()) : byId.get(item.getKbId());
                if (base != null) {
                    item.setKbId(base.getId());
                    item.setKbName(base.getName());
                    if (item.getCollectionName() == null) item.setCollectionName(base.getCollectionName());
                }
                item.setProvenanceStatus(document != null && base != null ? ProvenanceStatus.FULL : ProvenanceStatus.PARTIAL);
            }
        } catch (Exception ex) {
            evidence.forEach(item -> item.setProvenanceStatus(ProvenanceStatus.PARTIAL));
            log.warn("Evidence provenance enrichment failed; retaining partial evidence", ex);
        }
        return evidence;
    }
}
