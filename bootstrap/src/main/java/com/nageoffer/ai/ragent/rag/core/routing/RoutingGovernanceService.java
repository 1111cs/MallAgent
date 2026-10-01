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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RoutingGovernanceService {
    private final ResourceContractGuard contractGuard;

    public GovernanceOutcome decide(String originalQuery, String rewrittenQuery,
                                    IntentResolutionResult resolution) {
        String normalized = normalize(originalQuery);
        // This request shape is deterministic and does not depend on the classifier.
        // Keep it ahead of CLASSIFIER_ERROR so a transient model failure cannot turn a
        // clearly incomplete request into a routing-service error.
        if (isIncomplete(normalized)) {
            return outcome(RoutingDecision.NEED_CLARIFICATION,
                    RoutingReason.INCOMPLETE_BUSINESS_REQUEST,
                    "请说明要查询的商城业务，例如商品、订单、物流、保修、维修或优惠券。",
                    List.of(), List.of());
        }
        if (resolution.status() == IntentResolutionStatus.CLASSIFIER_ERROR) {
            return outcome(RoutingDecision.ROUTING_ERROR, RoutingReason.CLASSIFIER_ERROR,
                    "路由服务暂时不可用，请稍后重试。", List.of(), List.of());
        }
        ResourceContractGuard.GuardResult guard = contractGuard.authorize(
                originalQuery, rewrittenQuery, resolution.intents());
        if (!guard.allowedRoutes().isEmpty()) {
            RoutingDecisionResult result = RoutingDecisionResult.builder()
                    .decision(RoutingDecision.ROUTED).reason(guard.reason())
                    .allowedRoutes(guard.allowedRoutes()).build();
            return new GovernanceOutcome(result, guard.authorizedIntents());
        }
        if (isPublicMallKnowledge(normalized)) {
            RoutingDecisionResult result = RoutingDecisionResult.builder()
                    .decision(RoutingDecision.GLOBAL_KB_FALLBACK)
                    .reason(RoutingReason.PUBLIC_KNOWLEDGE_NO_TRUSTED_INTENT).build();
            List<SubQuestionIntent> empty = resolution.intents().isEmpty()
                    ? List.of(new SubQuestionIntent(rewrittenQuery, List.of())) : resolution.intents();
            return new GovernanceOutcome(result, empty);
        }
        return outcome(RoutingDecision.OUT_OF_SCOPE, RoutingReason.OUT_OF_BUSINESS_SCOPE,
                "这个请求不在 Huawei Mall 助手当前能力范围内。我可以协助商品、商城规则、订单物流、售后和设备支持问题。",
                List.of(), List.of());
    }

    private GovernanceOutcome outcome(RoutingDecision decision, RoutingReason reason, String response,
                                      List<AllowedRoute> routes, List<SubQuestionIntent> intents) {
        return new GovernanceOutcome(RoutingDecisionResult.builder().decision(decision).reason(reason)
                .allowedRoutes(routes).fixedResponse(response).build(), intents);
    }

    private boolean isIncomplete(String value) {
        return value.matches("^(帮我)?(查一下|看一下|看看|处理一下)$")
                || value.matches("^(这个|那个)(怎么处理|怎么办|怎么样)$");
    }

    private boolean isPublicMallKnowledge(String value) {
        boolean domain = containsAny(value, "商城", "商品", "购买", "配送", "售后", "保修", "退货", "换货",
                "优惠券", "发票", "耳机", "手机", "笔记本", "手表", "freebuds", "matebook");
        boolean privateState = containsAny(value, "我的订单", "我的物流", "我的设备", "我的优惠券", "我的维修",
                "订单号", "sn", "维修单", "到哪了", "进度", "库存", "有没有货");
        return domain && !privateState;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、]+", "");
    }

    public record GovernanceOutcome(RoutingDecisionResult decision,
                                    List<SubQuestionIntent> authorizedIntents) { }
}

