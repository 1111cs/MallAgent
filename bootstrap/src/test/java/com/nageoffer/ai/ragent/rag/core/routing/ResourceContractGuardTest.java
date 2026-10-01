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
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResourceContractGuardTest {
    private final Map<String, IntentNode> nodes = Map.of(
            "ORDER_QUERY", node("ORDER_QUERY", IntentKind.MCP, null, "getOrder"),
            "LOGISTICS_QUERY", node("LOGISTICS_QUERY", IntentKind.MCP, null, "getLogistics"),
            "CONNECTION_NETWORK", node("CONNECTION_NETWORK", IntentKind.KB, "support-kb", null),
            "PRODUCT_RECOMMEND_KB", node("PRODUCT_RECOMMEND_KB", IntentKind.KB, "product-kb", null),
            "SEARCH_PRODUCTS", node("SEARCH_PRODUCTS", IntentKind.MCP, null, "searchProducts"));
    private final ResourceContractGuard guard = new ResourceContractGuard(nodes::get);

    @Test void originalLogisticsPredicateOverridesLossyRewrite() {
        var input = List.of(new SubQuestionIntent("订单状态", List.of(
                new NodeScore(nodes.get("ORDER_QUERY"), .85))));
        var result = guard.authorize("订单 202609140001 到哪了？", "订单状态", input);
        assertEquals("LOGISTICS_QUERY", result.allowedRoutes().get(0).intentId());
        assertEquals(RoutingReason.REWRITE_CONFLICT, result.reason());
    }

    @Test void removesHiddenSecondaryToolAndCorrectsSupportLeaf() {
        var recommend = List.of(new SubQuestionIntent("适合写代码还是剪视频", List.of(
                new NodeScore(nodes.get("PRODUCT_RECOMMEND_KB"), .85),
                new NodeScore(nodes.get("SEARCH_PRODUCTS"), .55))));
        assertEquals(List.of("product-kb"), guard.authorize("适合写代码还是剪视频", "", recommend)
                .allowedRoutes().stream().map(AllowedRoute::resourceName).toList());
        assertEquals("CONNECTION_NETWORK", guard.authorize("FreeBuds 连不上手机怎么排查", "", List.of())
                .allowedRoutes().get(0).intentId());
    }

    private static IntentNode node(String id, IntentKind kind, String kb, String tool) {
        return IntentNode.builder().id(id).kind(kind).collectionName(kb).mcpToolId(tool).build();
    }
}
