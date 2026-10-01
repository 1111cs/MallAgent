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

import com.nageoffer.ai.ragent.rag.core.mcp.HuaweiMallRuntimeArguments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityResolverTest {
    private final EntityCandidateProvider provider = new EntityCandidateProvider() {
        @Override public List<EntityCandidate> findProducts() {
            return List.of(
                    product("P-LAPTOP-001", "HUAWEI MateBook X Pro 2026", "MateBook X Pro 2026"),
                    product("P-LAPTOP-003", "HUAWEI MateBook X 2026", "MateBook X 2026"));
        }
        @Override public List<EntityCandidate> findOwnedDevices(String principalId) {
            return List.of(
                    new EntityCandidate("D1", "HUAWEI WATCH GT 6（购买日期 2026-08-10）",
                            Map.of("sn", "WATCH-1", "productId", "P-WATCH-001", "model", "WATCH GT 6", "category", "wearable")),
                    new EntityCandidate("D2", "HUAWEI WATCH FIT 4（购买日期 2026-08-20）",
                            Map.of("sn", "WATCH-2", "productId", "P-WATCH-002", "model", "WATCH FIT 4", "category", "wearable")),
                    new EntityCandidate("D3", "HUAWEI MateBook X Pro 2026（购买日期 2026-05-03）",
                            Map.of("sn", "LAP-1", "productId", "P-LAPTOP-001", "model", "MateBook X Pro 2026", "category", "laptop")),
                    new EntityCandidate("D4", "HUAWEI MateBook 14 2026（购买日期 2026-07-05）",
                            Map.of("sn", "LAP-2", "productId", "P-LAPTOP-002", "model", "MateBook 14 2026", "category", "laptop")));
        }
        @Override public List<EntityCandidate> findOwnedCoupons(String principalId) {
            return List.of(
                    new EntityCandidate("UC1", "满3000减300（UNUSED）", Map.of("couponId", "C001")),
                    new EntityCandidate("UC2", "满5000减600（UNUSED）", Map.of("couponId", "C002")));
        }
        private EntityCandidate product(String id, String label, String model) {
            return new EntityCandidate(id, label,
                    Map.of("productId", id, "model", model, "category", "laptop"));
        }
    };
    private final EntityResolver resolver = new EntityResolver(
            provider, new HuaweiMallRuntimeArguments("login=U002", "01234567890123456789012345678901"));

    @Test void normalizesProductNameInToolDomain() {
        var result = resolver.resolve(new EntityResolutionRequest(
                "getSparePartPrice", "productId", "MateBook X Pro 换屏多少钱", "login"));
        assertEquals(EntityResolutionStatus.UNIQUE, result.status());
        assertEquals("P-LAPTOP-001", result.selected().bindings().get("productId"));
    }

    @Test void discoversTrustedAmbiguousDevices() {
        var result = resolver.resolve(new EntityResolutionRequest(
                "getWarrantyStatus", "sn", "我的手表还在不在保", "login"));
        assertEquals(EntityResolutionStatus.AMBIGUOUS, result.status());
        assertEquals(2, result.candidates().size());
    }

    @Test void genericLaptopRemainsAmbiguousAfterRewriteDropsPossessive() {
        var result = resolver.resolve(new EntityResolutionRequest(
                "getSparePartPrice", "productId", "查询笔记本换屏价格", "login"));
        assertEquals(EntityResolutionStatus.AMBIGUOUS, result.status());
        assertEquals(2, result.candidates().size());
    }

    @Test void genericDeviceRemainsMissingForD1() {
        var result = resolver.resolve(new EntityResolutionRequest(
                "getWarrantyStatus", "sn", "帮我看看我的设备还在不在保", "login"));
        assertEquals(EntityResolutionStatus.NOT_FOUND, result.status());
    }

    @Test void discoversOwnedCouponCandidates() {
        var result = resolver.resolve(new EntityResolutionRequest(
                "getCouponEligibility", "couponId", "为什么我的优惠券不能用", "login"));
        assertEquals(EntityResolutionStatus.AMBIGUOUS, result.status());
        assertEquals(2, result.candidates().size());
    }
}
