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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ParameterCheckResult(boolean ready, Set<String> missingAll,
                                   List<RequiredAnyGroup> unsatisfiedAnyGroups) {
    public ParameterCheckResult {
        missingAll = missingAll == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(missingAll));
        unsatisfiedAnyGroups = unsatisfiedAnyGroups == null ? List.of() : List.copyOf(unsatisfiedAnyGroups);
    }

    public Set<String> missingKeys() {
        LinkedHashSet<String> result = new LinkedHashSet<>(missingAll);
        unsatisfiedAnyGroups.forEach(group -> result.addAll(group.alternatives()));
        return result;
    }
}
