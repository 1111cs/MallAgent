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

package com.nageoffer.ai.ragent.rag.core.entity;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SelectionPromptBuilder {
    public String build(String entityType, List<EntityCandidate> candidates) {
        String noun = switch (entityType) {
            case "DEVICE" -> "设备";
            case "COUPON" -> "优惠券";
            case "PRODUCT" -> "商品";
            default -> "候选项";
        };
        StringBuilder prompt = new StringBuilder("找到多个符合条件的").append(noun).append("，请选择：\n");
        for (int i = 0; i < candidates.size(); i++) {
            prompt.append(i + 1).append(". ").append(candidates.get(i).displayLabel());
            if (i + 1 < candidates.size()) prompt.append("\n");
        }
        return prompt.toString();
    }
}
