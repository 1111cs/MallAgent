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

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Removes deictic or cross-field identifier guesses before the missing-parameter gate. */
@Component
public class ExactBusinessParameterFilter {
    private static final Set<String> DEICTIC = Set.of(
            "这个", "那个", "我的", "它", "该", "当前", "未知", "这个优惠券", "这张优惠券", "我的优惠券");

    public Map<String, Object> filter(Map<String, Object> extracted) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (extracted == null) return safe;
        extracted.forEach((key, value) -> {
            if (acceptable(key, value)) safe.put(key, value);
        });
        return safe;
    }

    private boolean acceptable(String key, Object value) {
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty() || DEICTIC.contains(normalized)) return false;
            return switch (key) {
                case "couponId" -> normalized.matches("C\\d+");
                case "orderNo" -> normalized.matches("\\d{12}");
                case "productId" -> normalized.matches("P-[A-Z0-9-]+");
                case "serviceNo" -> normalized.matches("WX\\d+");
                case "phone" -> normalized.matches("1\\d{10}");
                default -> true;
            };
        }
        if ("productIds".equals(key) && value instanceof Collection<?> values) {
            return nonEmptyAndAll(values, item -> item != null && item.toString().matches("P-[A-Z0-9-]+"));
        }
        return value != null;
    }

    private boolean nonEmptyAndAll(Collection<?> values, Predicate<Object> predicate) {
        return !values.isEmpty() && values.stream().allMatch(predicate);
    }
}
