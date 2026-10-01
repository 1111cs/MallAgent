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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencySourceMatcherTest {
    private final DependencySourceMatcher matcher = new DependencySourceMatcher();

    @Test void matchesOnlyFrozenLastMonthPurchaseSemantics() {
        var watch = matcher.match("帮我看看上个月买的手表还在不在保").orElseThrow();
        assertEquals("LAST_MONTH", watch.timeRange());
        assertEquals("wearable", watch.category());
        assertEquals("laptop", matcher.match("上月购买的笔记本换屏多少钱").orElseThrow().category());
    }

    @Test void doesNotStealD2OrInventUnsupportedTimeRanges() {
        assertTrue(matcher.match("我的手表还在不在保").isEmpty());
        assertTrue(matcher.match("之前买的笔记本换屏多少钱").isEmpty());
        assertTrue(matcher.match("上个月用过的手表还在不在保").isEmpty());
    }
}
