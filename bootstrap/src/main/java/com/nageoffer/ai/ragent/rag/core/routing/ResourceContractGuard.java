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

package com.nageoffer.ai.ragent.rag.core.routing;

import com.nageoffer.ai.ragent.rag.core.intent.*;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ResourceContractGuard {
    private final IntentNodeRegistry nodeRegistry;

    public GuardResult authorize(String originalQuery, String rewrittenQuery,
                                 List<SubQuestionIntent> candidates) {
        List<SubQuestionIntent> authorized = new ArrayList<>();
        List<AllowedRoute> routes = new ArrayList<>();
        RoutingReason reason = RoutingReason.NONE;
        boolean single = candidates == null || candidates.size() <= 1;
        List<SubQuestionIntent> source = candidates == null ? List.of() : candidates;
        if (source.isEmpty()) source = List.of(new SubQuestionIntent(originalQuery, List.of()));

        for (SubQuestionIntent item : source) {
            String semanticQuery = single ? originalQuery : item.subQuestion();
            String forcedId = forcedIntent(semanticQuery);
            NodeScore chosen = null;
            if (forcedId != null) {
                IntentNode forced = nodeRegistry.getNodeById(forcedId);
                if (forced != null) {
                    double score = item.nodeScores().stream()
                            .filter(ns -> forcedId.equals(ns.getNode().getId()))
                            .mapToDouble(NodeScore::getScore).findFirst().orElse(1D);
                    chosen = new NodeScore(forced, score);
                    String topId = item.nodeScores().isEmpty() ? null : item.nodeScores().get(0).getNode().getId();
                    if (topId != null && !forcedId.equals(topId)) {
                        reason = differsByRewrite(originalQuery, rewrittenQuery, forcedId)
                                ? RoutingReason.REWRITE_CONFLICT : RoutingReason.RESOURCE_CONTRACT_CONFLICT;
                    }
                }
            }
            if (chosen == null && !item.nodeScores().isEmpty()) {
                chosen = chooseCandidate(item.nodeScores());
                if (item.nodeScores().size() > 1) reason = RoutingReason.RESOURCE_CONTRACT_CONFLICT;
            }
            if (chosen == null) continue;
            SubQuestionIntent kept = new SubQuestionIntent(item.subQuestion(), List.of(chosen));
            authorized.add(kept);
            routes.add(toRoute(kept.subQuestion(), chosen.getNode()));
        }
        return new GuardResult(authorized, routes, reason);
    }

    private NodeScore chooseCandidate(List<NodeScore> scores) {
        NodeScore top = scores.get(0);
        if ("PRODUCT_RECOMMEND_KB".equals(top.getNode().getId())) return top;
        return top;
    }

    private String forcedIntent(String query) {
        String q = normalize(query);
        if (containsAny(q, "到哪了", "物流", "配送进度", "快递", "运输", "什么时候送到")) return "LOGISTICS_QUERY";
        if (q.contains("订单") && containsAny(q, "查", "详情", "金额", "买了什么", "下单时间", "订单号", "状态详情")) return "ORDER_QUERY";
        if (containsAny(q, "搜索不到", "配对失败", "连不上", "wifi连不上", "蓝牙连接失败")) return "CONNECTION_NETWORK";
        if (containsAny(q, "单耳无声", "频繁重启", "已经连上但没有声音", "使用过程中异常断连", "音频断续")) return "TROUBLESHOOTING";
        if (containsAny(q, "怎么使用", "如何设置", "怎么开启", "操作步骤")) return "USAGE_GUIDE";
        return null;
    }

    private boolean differsByRewrite(String original, String rewritten, String forcedId) {
        if (!"LOGISTICS_QUERY".equals(forcedId)) return false;
        return containsAny(normalize(original), "到哪了", "物流", "配送", "快递", "运输")
                && !containsAny(normalize(rewritten), "到哪了", "物流", "配送", "快递", "运输");
    }

    private AllowedRoute toRoute(String subQuestion, IntentNode node) {
        String resource = node.isMCP() ? node.getMcpToolId()
                : node.isKB() ? node.getCollectionName() : node.getId();
        return new AllowedRoute(subQuestion, node.getId(), node.getKind(), resource);
    }

    private boolean containsAny(String value, String... terms) {
        return Arrays.stream(terms).anyMatch(value::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、]+", "");
    }

    public record GuardResult(List<SubQuestionIntent> authorizedIntents,
                              List<AllowedRoute> allowedRoutes,
                              RoutingReason reason) { }
}

