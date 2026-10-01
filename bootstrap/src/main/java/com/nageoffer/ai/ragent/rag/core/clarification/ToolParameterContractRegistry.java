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

package com.nageoffer.ai.ragent.rag.core.clarification;

import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ToolParameterContractRegistry {

    private static final Map<String, List<RequiredAnyGroup>> REQUIRED_ANY = Map.of(
            "getRepairProgress", List.of(group(Set.of("serviceNo", "phone"),
                    "维修服务单号或预约时填写的手机号")),
            "getCouponEligibility", List.of(group(Set.of("orderNo", "productIds"),
                    "想使用优惠券的订单号或商品")),
            "getSparePartPrice", List.of(group(Set.of("productId", "model"),
                    "具体商品或型号"))
    );

    public ToolParameterContract resolve(Tool tool) {
        Set<String> requiredAll = tool == null || tool.inputSchema() == null
                || tool.inputSchema().required() == null
                ? Set.of() : new LinkedHashSet<>(tool.inputSchema().required());
        String toolId = tool == null ? "" : tool.name();
        return new ToolParameterContract(requiredAll, REQUIRED_ANY.getOrDefault(toolId, List.of()));
    }

    private static RequiredAnyGroup group(Set<String> alternatives, String description) {
        return new RequiredAnyGroup(alternatives, description);
    }
}
