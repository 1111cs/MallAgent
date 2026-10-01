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

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Removes invalid citation tokens only; it never rewrites claims. */
@Component
public class CitationCleanupService {

    private static final Pattern CITATION_ONLY_LINE = Pattern.compile(
            "(?m)^[\\t ]*((?:\\[E\\d+]))(?:[\\t ]+(?:\\[E\\d+]))*[\\t ]*$");
    private static final Pattern CITATION_TOKEN = Pattern.compile("\\[(E\\d+)]");
    private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile("[。！？；：.!?;:]$");
    private static final Pattern POST_PUNCTUATION_CITATION = Pattern.compile("([。！？!?\\.])\\s*(\\[(E\\d+)])");

    public String removeInvalidTokens(String answer, List<String> invalidEvidenceIds) {
        String cleaned = answer == null ? "" : answer;
        if (invalidEvidenceIds != null) {
            for (String id : invalidEvidenceIds) cleaned = cleaned.replace("[" + id + "]", "");
        }
        return cleaned.replaceAll("[ \\t]{2,}", " ")
                .replaceAll("[ \\t]+([，。！？；：,.!?;:])", "$1");
    }

    /**
     * Moves a citation-only line to the immediately preceding non-empty paragraph.
     * Only citation IDs already validated against this request's evidence set are moved.
     */
    public CleanupResult relocateStandaloneValidCitations(String answer, Set<String> validEvidenceIds) {
        String normalized = answer == null ? "" : answer;
        if (normalized.isBlank() || validEvidenceIds == null || validEvidenceIds.isEmpty()) {
            return new CleanupResult(normalized, false);
        }

        String lineSeparator = normalized.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = normalized.split("\\R", -1);
        boolean changed = false;
        for (int index = 0; index < lines.length; index++) {
            String citationLine = lines[index].trim();
            if (!isCitationOnlyLine(citationLine, validEvidenceIds)) continue;

            int previous = index - 1;
            while (previous >= 0 && lines[previous].isBlank()) previous--;
            if (previous < 0 || isCitationOnlyLine(lines[previous].trim(), validEvidenceIds)) continue;

            lines[previous] = appendBeforeTerminalPunctuation(lines[previous], citationLine);
            lines[index] = "";
            changed = true;
        }
        if (!changed) return new CleanupResult(normalized, false);

        String relocated = String.join(lineSeparator, lines)
                .replaceAll("(?:\\r?\\n){3,}", lineSeparator + lineSeparator)
                .stripTrailing();
        return new CleanupResult(relocated, true);
    }

    /** Moves a valid citation immediately following sentence punctuation before that punctuation. */
    public CleanupResult normalizePostPunctuationCitations(String answer, Set<String> validEvidenceIds) {
        String normalized = answer == null ? "" : answer;
        if (normalized.isBlank() || validEvidenceIds == null || validEvidenceIds.isEmpty()) {
            return new CleanupResult(normalized, false);
        }
        Matcher matcher = POST_PUNCTUATION_CITATION.matcher(normalized);
        StringBuffer result = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            if (!validEvidenceIds.contains(matcher.group(3))) continue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(" " + matcher.group(2) + matcher.group(1)));
            changed = true;
        }
        if (!changed) return new CleanupResult(normalized, false);
        matcher.appendTail(result);
        return new CleanupResult(result.toString(), true);
    }

    private boolean isCitationOnlyLine(String line, Set<String> validEvidenceIds) {
        if (!CITATION_ONLY_LINE.matcher(line).matches()) return false;
        Matcher matcher = CITATION_TOKEN.matcher(line);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (!validEvidenceIds.contains(matcher.group(1))) return false;
        }
        return found;
    }

    private String appendBeforeTerminalPunctuation(String line, String citations) {
        String trimmed = line.stripTrailing();
        Matcher punctuation = TERMINAL_PUNCTUATION.matcher(trimmed);
        if (punctuation.find()) {
            return trimmed.substring(0, punctuation.start()) + " " + citations + punctuation.group();
        }
        return trimmed + " " + citations;
    }

    public record CleanupResult(String answer, boolean applied) {
    }
}
