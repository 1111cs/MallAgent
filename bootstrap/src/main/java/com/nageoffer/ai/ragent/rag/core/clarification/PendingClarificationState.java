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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

import com.nageoffer.ai.ragent.rag.core.entity.EntityCandidate;
import com.nageoffer.ai.ragent.rag.core.dependency.DependencyPendingContext;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingClarificationState {
    private String pendingTaskId;
    private String authenticatedUserId;
    private String conversationId;
    private String intent;
    private String toolId;
    private String originalQuery;
    @Builder.Default
    private Map<String, Object> resolvedArguments = new LinkedHashMap<>();
    @Builder.Default
    private Set<String> clarificationParameters = new LinkedHashSet<>();
    private ParameterCheckResult missingRequirements;
    @Builder.Default
    private PendingReason pendingReason = PendingReason.NEED_PARAMETERS;
    private String selectionTarget;
    private String entityType;
    @Builder.Default
    private List<EntityCandidate> candidates = List.of();
    @Builder.Default
    private Set<String> entityResolutionParameters = new LinkedHashSet<>();
    private DependencyPendingContext dependencyContext;
}
