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

import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CitationValidator {

    private final CitationParser citationParser;
    private final CitationCleanupService cleanupService;

    public CitationValidationResult validate(String rawAnswer, List<EvidenceRecord> evidence,
                                               boolean citationRequired) {
        CitationParseResult parsed = citationParser.parse(rawAnswer);
        Set<String> allowed = new LinkedHashSet<>();
        if (evidence != null) {
            evidence.stream().filter(EvidenceRecord::isSelectedForPrompt)
                    .map(EvidenceRecord::getEvidenceId).filter(id -> id != null && !id.isBlank())
                    .forEach(allowed::add);
        }
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String id : parsed.usedEvidenceIds()) (allowed.contains(id) ? valid : invalid).add(id);

        CitationInitialStatus initial = !invalid.isEmpty() ? CitationInitialStatus.INVALID_REFERENCE
                : parsed.usedEvidenceIds().isEmpty() ? CitationInitialStatus.NO_CITATION : CitationInitialStatus.VALID;
        boolean invalidCleanupApplied = !invalid.isEmpty();
        String cleanedAnswer = invalidCleanupApplied ? cleanupService.removeInvalidTokens(rawAnswer, invalid)
                : (rawAnswer == null ? "" : rawAnswer);
        CitationCleanupService.CleanupResult punctuation = cleanupService.normalizePostPunctuationCitations(
                cleanedAnswer, Set.copyOf(valid));
        CitationCleanupService.CleanupResult relocation = cleanupService.relocateStandaloneValidCitations(
                punctuation.answer(), Set.copyOf(valid));
        boolean cleanupApplied = invalidCleanupApplied || punctuation.applied() || relocation.applied();
        String finalAnswer = relocation.answer();
        CitationParseResult finalParsed = citationParser.parse(finalAnswer);
        List<String> finalValid = finalParsed.usedEvidenceIds().stream().filter(allowed::contains).toList();
        CitationFinalStatus finalStatus = invalidCleanupApplied ? CitationFinalStatus.INVALID_REFERENCE_CLEANED
                : !finalValid.isEmpty() ? CitationFinalStatus.VALID
                : citationRequired && !allowed.isEmpty() ? CitationFinalStatus.MISSING_REQUIRED_CITATION
                : CitationFinalStatus.NO_CITATION_REQUIRED;
        return new CitationValidationResult(finalParsed.usedEvidenceIds(), List.copyOf(finalValid), List.copyOf(invalid),
                initial, finalStatus, cleanupApplied, finalAnswer);
    }
}
