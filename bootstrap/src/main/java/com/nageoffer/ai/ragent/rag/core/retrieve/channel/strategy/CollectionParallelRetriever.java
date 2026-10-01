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

package com.nageoffer.ai.ragent.rag.core.retrieve.channel.strategy;

import cn.hutool.crypto.SecureUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceRecord;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.AbstractParallelRetriever;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import com.nageoffer.ai.ragent.rag.core.routing.RoutingDecision;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Collection 并行检索器
 * 继承模板类，实现 Collection 特定的检索逻辑
 */
@Slf4j
public class CollectionParallelRetriever extends AbstractParallelRetriever<CollectionParallelRetriever.CollectionTask, EvidenceRecord> {

    private final RetrieverService retrieverService;

    public CollectionParallelRetriever(RetrieverService retrieverService, Executor executor) {
        super(executor);
        this.retrieverService = retrieverService;
    }

    public record CollectionTask(String collectionName, SearchContext searchContext) { }

    public List<EvidenceRecord> executeParallelRetrieval(String question, List<String> collections,
                                                          int topK, SearchContext context) {
        return super.executeParallelRetrieval(question,
                collections.stream().map(c -> new CollectionTask(c, context)).toList(), topK);
    }

    @Override
    protected List<EvidenceRecord> createRetrievalTask(String question, CollectionTask task, int topK) {
        try {
            List<RetrievedChunk> chunks = retrieverService.retrieve(
                    RetrieveRequest.builder()
                            .collectionName(task.collectionName())
                            .query(question)
                            .topK(topK)
                            .build()
            );
            return java.util.stream.IntStream.range(0, chunks.size()).mapToObj(i -> {
                RetrievedChunk chunk = chunks.get(i);
                return EvidenceRecord.builder()
                        .routingDecision(task.searchContext().getRoutingDecision() == null
                                ? RoutingDecision.GLOBAL_KB_FALLBACK : task.searchContext().getRoutingDecision().getDecision())
                        .subQuestion(question).retrievalChannel(SearchChannelType.VECTOR_GLOBAL.name())
                        .collectionName(chunk.getCollectionName() == null ? task.collectionName() : chunk.getCollectionName())
                        .documentId(chunk.getDocumentId()).chunkId(chunk.getId()).chunkIndex(chunk.getChunkIndex())
                        .retrievalScore(chunk.getScore() == null ? null : chunk.getScore().doubleValue())
                        .retrievalRank(i + 1).content(chunk.getText())
                        .contentHash(SecureUtil.sha256(chunk.getText() == null ? "" : chunk.getText())).build();
            }).toList();
        } catch (Exception e) {
            log.error("在 collection {} 中检索失败，错误: {}", task.collectionName(), e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    protected String getTargetIdentifier(CollectionTask task) {
        return "Collection: " + task.collectionName();
    }

    @Override
    protected String getStatisticsName() {
        return "全局检索";
    }
}
