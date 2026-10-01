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

package com.nageoffer.ai.ragent.rag.core.retrieve.postprocessor;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.framework.convention.RerankResult;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceRecord;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rerank 后置处理器
 * <p>
 * 使用 Rerank 模型对结果进行重排序
 * 这是最后一个处理器，输出最终的 Top-K 结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankPostProcessor implements SearchResultPostProcessor {

    private final RerankService rerankService;
    private final RAGConfigProperties ragConfigProperties;

    @Override
    public String getName() {
        return "Rerank";
    }

    @Override
    public int getOrder() {
        return 10;  // 最后执行
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        return ragConfigProperties.getRerankEnabled();
    }

    @Override
    public List<EvidenceRecord> process(List<EvidenceRecord> chunks,
                                        List<SearchChannelResult> results,
                                        SearchContext context) {
        if (chunks.isEmpty()) {
            log.info("Chunk 列表为空，跳过 Rerank");
            return chunks;
        }

        Map<String, List<EvidenceRecord>> byOrigin = new LinkedHashMap<>();
        for (EvidenceRecord item : chunks) byOrigin.computeIfAbsent(item.rankingScopeKey(), key -> new ArrayList<>()).add(item);
        List<EvidenceRecord> ordered = new ArrayList<>();
        for (List<EvidenceRecord> group : byOrigin.values()) {
            List<RetrievedChunk> candidates = group.stream().map(item -> new RetrievedChunk(
                    item.getChunkId(), item.getContent(), item.getRetrievalScore() == null ? null : item.getRetrievalScore().floatValue(),
                    item.getDocumentId(), item.getCollectionName(), item.getChunkIndex())).toList();
            List<RerankResult> ranking = rerankService.rerank(context.getMainQuestion(), candidates, candidates.size());
            boolean[] included = new boolean[group.size()];
            int rank = 0;
            for (RerankResult result : ranking) {
                if (result.candidateIndex() < 0 || result.candidateIndex() >= group.size() || included[result.candidateIndex()]) continue;
                EvidenceRecord item = group.get(result.candidateIndex());
                item.setRerankScore(result.rerankScore() == null ? null : result.rerankScore().doubleValue());
                item.setFinalRank(++rank);
                item.setSelectedForPrompt(rank <= context.getTopK());
                ordered.add(item);
                included[result.candidateIndex()] = true;
            }
            for (int i = 0; i < group.size(); i++) {
                if (!included[i]) {
                    EvidenceRecord item = group.get(i);
                    item.setFinalRank(++rank);
                    item.setSelectedForPrompt(rank <= context.getTopK());
                    ordered.add(item);
                }
            }
        }
        return ordered;
    }
}
