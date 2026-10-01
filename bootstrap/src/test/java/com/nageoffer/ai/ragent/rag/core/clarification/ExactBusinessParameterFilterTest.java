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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExactBusinessParameterFilterTest {
    private final ExactBusinessParameterFilter filter = new ExactBusinessParameterFilter();

    @Test void rejectsDeicticAndCrossFieldIdentifierGuesses() {
        assertFalse(filter.filter(Map.of("couponId", "这个优惠券")).containsKey("couponId"));
        assertFalse(filter.filter(Map.of("productIds", List.of("C001"))).containsKey("productIds"));
        assertTrue(filter.filter(Map.of("couponId", "C001")).containsKey("couponId"));
        assertTrue(filter.filter(Map.of("orderNo", "202609140001")).containsKey("orderNo"));
    }
}
