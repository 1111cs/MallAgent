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

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ClarificationPromptBuilderTest {
    @Test void combinesMissingRequirementsWithoutInternalErrors() {
        var builder = new ClarificationPromptBuilder(new ParameterDescriptorRegistry());
        var result = new ParameterCheckResult(false, Set.of("couponId"),
                List.of(new RequiredAnyGroup(Set.of("orderNo", "productIds"), "想使用优惠券的订单号或商品")));
        String prompt = builder.build(result);
        assertTrue(prompt.contains("优惠券"));
        assertTrue(prompt.contains("订单号或商品"));
        assertFalse(prompt.contains("VALIDATION_ERROR"));
        assertFalse(prompt.contains("couponId"));
    }
}
