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
import java.util.Locale;
import java.util.Optional;

@Component
public class SelectionResolver {
    private static final List<String> ORDINALS = List.of("第一", "第二", "第三", "第四", "第五");

    public Optional<EntityCandidate> resolve(String input, List<EntityCandidate> candidates) {
        if (input == null || candidates == null || candidates.isEmpty()) return Optional.empty();
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < candidates.size(); i++) {
            EntityCandidate candidate = candidates.get(i);
            if (normalized.contains(candidate.candidateId().toLowerCase(Locale.ROOT))
                    || normalized.contains(candidate.displayLabel().toLowerCase(Locale.ROOT))) return Optional.of(candidate);
            if (i < ORDINALS.size() && normalized.contains(ORDINALS.get(i))) return Optional.of(candidate);
            if (normalized.matches(".*(^|\\D)" + (i + 1) + "(\\D|$).*") || normalized.equals(String.valueOf(i + 1)))
                return Optional.of(candidate);
        }
        return Optional.empty();
    }
}
