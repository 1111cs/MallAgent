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

import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.core.evidence.EvidenceRecord;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Buffers citation-aware answers until deterministic validation is complete. */
public final class CitationBufferedCallback implements StreamCallback {

    private final StreamCallback downstream;
    private final CitationValidator validator;
    private final List<EvidenceRecord> evidence;
    private final boolean citationRequired;
    private final Consumer<CitationValidationResult> onValidated;
    private final Runnable onFailed;
    private final StringBuilder answer = new StringBuilder();
    private final AtomicBoolean finished = new AtomicBoolean(false);

    public CitationBufferedCallback(StreamCallback downstream, CitationValidator validator,
                                    List<EvidenceRecord> evidence, boolean citationRequired,
                                    Consumer<CitationValidationResult> onValidated, Runnable onFailed) {
        this.downstream = downstream;
        this.validator = validator;
        this.evidence = evidence == null ? List.of() : evidence;
        this.citationRequired = citationRequired;
        this.onValidated = onValidated;
        this.onFailed = onFailed;
    }

    @Override
    public void onContent(String content) {
        if (!finished.get() && content != null) answer.append(content);
    }

    @Override
    public void onThinking(String content) {
        if (!finished.get()) downstream.onThinking(content);
    }

    @Override
    public void onComplete() {
        if (!finished.compareAndSet(false, true)) return;
        try {
            CitationValidationResult result = validator.validate(answer.toString(), evidence, citationRequired);
            onValidated.accept(result);
            downstream.onContent(result.finalAnswer());
            downstream.onComplete();
        } catch (Throwable error) {
            onFailed.run();
            downstream.onError(error);
        }
    }

    @Override
    public void onError(Throwable error) {
        if (!finished.compareAndSet(false, true)) return;
        onFailed.run();
        downstream.onError(error);
    }
}
