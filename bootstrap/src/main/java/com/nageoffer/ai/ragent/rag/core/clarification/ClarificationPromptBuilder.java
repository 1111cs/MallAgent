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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClarificationPromptBuilder {
    private final ParameterDescriptorRegistry descriptors;

    public String build(ParameterCheckResult result) {
        List<String> phrases = new ArrayList<>();
        result.missingAll().forEach(key -> phrases.add(descriptors.get(key).askPhrase()));
        result.unsatisfiedAnyGroups().forEach(group -> phrases.add("请提供" + group.userFacingDescription()));
        if (phrases.isEmpty()) return "请补充查询所需的信息。";
        String combined = phrases.size() == 1 ? phrases.get(0)
                : String.join("，", phrases.subList(0, phrases.size() - 1)) + "，以及" + phrases.get(phrases.size() - 1);
        return combined + "，我再继续为你查询。";
    }
}
