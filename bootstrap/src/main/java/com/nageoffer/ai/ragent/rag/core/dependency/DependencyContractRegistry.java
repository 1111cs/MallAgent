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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class DependencyContractRegistry {
    public Optional<DependencyContract> resolve(Set<String> requestedTools,
                                                DependencySourceConstraint source) {
        Set<String> supported = new LinkedHashSet<>();
        if (requestedTools.contains("getWarrantyStatus")) supported.add("getWarrantyStatus");
        if (requestedTools.contains("getSparePartPrice")) supported.add("getSparePartPrice");
        if (supported.isEmpty() || supported.size() != requestedTools.size()) return Optional.empty();
        List<ToolOutputBinding> bindings = new ArrayList<>();
        if (supported.contains("getWarrantyStatus")) bindings.add(new ToolOutputBinding(
                "getOrder", "data.orders[*].products[*].sn", "getWarrantyStatus", "sn"));
        if (supported.contains("getSparePartPrice")) bindings.add(new ToolOutputBinding(
                "getOrder", "data.orders[*].products[*].productId", "getSparePartPrice", "productId"));
        String id = supported.size() == 2 ? "DEP-WARRANTY-AND-SPARE-BY-ORDER"
                : supported.contains("getWarrantyStatus") ? "DEP-WARRANTY-BY-ORDER" : "DEP-SPARE-BY-ORDER";
        return Optional.of(new DependencyContract(id, "getOrder", source.category(),
                List.copyOf(supported), bindings));
    }
}
