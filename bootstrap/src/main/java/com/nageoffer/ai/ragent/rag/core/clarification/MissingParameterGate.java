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

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class MissingParameterGate {

    public ParameterCheckResult check(ToolParameterContract contract, Map<String, Object> arguments) {
        Map<String, Object> actual = arguments == null ? Map.of() : arguments;
        LinkedHashSet<String> missingAll = new LinkedHashSet<>();
        for (String key : contract.requiredAll()) {
            if (isMissing(actual.get(key))) {
                missingAll.add(key);
            }
        }
        List<RequiredAnyGroup> unsatisfied = contract.requiredAny().stream()
                .filter(group -> group.alternatives().stream().noneMatch(key -> !isMissing(actual.get(key))))
                .toList();
        return new ParameterCheckResult(missingAll.isEmpty() && unsatisfied.isEmpty(), missingAll, unsatisfied);
    }

    boolean isMissing(Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence text) return text.toString().isBlank();
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        if (value instanceof Map<?, ?> map) return map.isEmpty();
        return value.getClass().isArray() && Array.getLength(value) == 0;
    }
}
