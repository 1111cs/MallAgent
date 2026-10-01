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

package com.nageoffer.ai.ragent.rag.core.routing;

import com.nageoffer.ai.ragent.rag.core.mcp.HuaweiMallRuntimeArguments;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreRoutingPolicyTest {
    private final PreRoutingPolicy policy = new PreRoutingPolicy(new HuaweiMallRuntimeArguments(
            "login=U001", "01234567890123456789012345678901"));

    @Test void handlesPureIdentityAndSystemMetaDeterministically() {
        var identity = policy.decide("我的用户编号是多少？", "login").orElseThrow();
        assertEquals(RoutingDecision.AUTH_CONTEXT, identity.getDecision());
        assertTrue(identity.getFixedResponse().contains("U001"));
        assertEquals(RoutingDecision.SYSTEM,
                policy.decide("你能帮我处理哪些商城问题？", "login").orElseThrow().getDecision());
    }

    @Test void doesNotHijackBusinessRequests() {
        assertTrue(policy.decide("我的账号为什么登录不了？", "login").isEmpty());
        assertTrue(policy.decide("你能查一下我的订单吗？", "login").isEmpty());
    }
}
