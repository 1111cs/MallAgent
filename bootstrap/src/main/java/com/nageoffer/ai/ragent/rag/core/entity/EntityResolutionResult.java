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

import java.util.List;

public record EntityResolutionResult(EntityResolutionStatus status, EntityCandidate selected,
                                     List<EntityCandidate> candidates, String entityType) {
    public EntityResolutionResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static EntityResolutionResult unique(EntityCandidate selected, List<EntityCandidate> candidates,
                                                String entityType) {
        return new EntityResolutionResult(EntityResolutionStatus.UNIQUE, selected, candidates, entityType);
    }

    public static EntityResolutionResult ambiguous(List<EntityCandidate> candidates, String entityType) {
        return new EntityResolutionResult(EntityResolutionStatus.AMBIGUOUS, null, candidates, entityType);
    }

    public static EntityResolutionResult notFound(String entityType) {
        return new EntityResolutionResult(EntityResolutionStatus.NOT_FOUND, null, List.of(), entityType);
    }
}
