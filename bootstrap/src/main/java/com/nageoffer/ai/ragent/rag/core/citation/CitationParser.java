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

package com.nageoffer.ai.ragent.rag.core.citation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CitationParser {

    /**
     * Recognizes strict citations and the explicitly rejected citation-like forms so they cannot
     * bypass validation and remain visible. Only exact values such as E1 can match the allowed set.
     */
    private static final Pattern CITATION = Pattern.compile(
            "\\[((?:[Ee]\\s*\\d+(?:\\s*(?:,|-)\\s*(?:[Ee]\\s*)?\\d+)*)|(?:Evidence\\s*\\d+))]",
            Pattern.CASE_INSENSITIVE);

    public CitationParseResult parse(String answer) {
        List<String> occurrences = new ArrayList<>();
        Matcher matcher = CITATION.matcher(answer == null ? "" : answer);
        while (matcher.find()) occurrences.add(matcher.group(1));
        return new CitationParseResult(List.copyOf(occurrences),
                List.copyOf(new LinkedHashSet<>(occurrences)));
    }
}
