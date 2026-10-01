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

package com.nageoffer.ai.ragent.rag.core.dependency;

import org.springframework.stereotype.Component;
import java.util.Locale;
import java.util.Optional;

@Component
public class DependencySourceMatcher {
    public Optional<DependencySourceConstraint> match(String query) {
        if (query == null) return Optional.empty();
        String value = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        boolean lastMonth = value.contains("上个月") || value.contains("上月");
        boolean purchase = value.contains("买") || value.contains("购买");
        if (!lastMonth || !purchase) return Optional.empty();
        if (value.contains("手表") || value.contains("watch"))
            return Optional.of(new DependencySourceConstraint("LAST_MONTH", "wearable"));
        if (value.contains("笔记本") || value.contains("matebook"))
            return Optional.of(new DependencySourceConstraint("LAST_MONTH", "laptop"));
        return Optional.empty();
    }
}
