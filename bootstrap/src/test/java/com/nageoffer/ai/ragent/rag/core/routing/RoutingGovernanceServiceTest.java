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

import com.nageoffer.ai.ragent.rag.core.intent.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoutingGovernanceServiceTest {
    private final RoutingGovernanceService service = new RoutingGovernanceService(
            new ResourceContractGuard(id -> null));

    @Test void separatesPublicFallbackIncompleteOutOfScopeAndClassifierError() {
        var noMatch = new IntentResolutionResult(IntentResolutionStatus.NO_TRUSTED_INTENT, List.of(), null);
        assertEquals(RoutingDecision.GLOBAL_KB_FALLBACK,
                service.decide("商城公开的购买和售后规则有哪些", "", noMatch).decision().getDecision());
        assertEquals(RoutingDecision.NEED_CLARIFICATION,
                service.decide("帮我查一下", "", noMatch).decision().getDecision());
        assertEquals(RoutingDecision.OUT_OF_SCOPE,
                service.decide("帮我订一张机票", "", noMatch).decision().getDecision());
        var failed = new IntentResolutionResult(IntentResolutionStatus.CLASSIFIER_ERROR, List.of(), "timeout");
        assertEquals(RoutingDecision.ROUTING_ERROR,
                service.decide("查订单", "", failed).decision().getDecision());
        assertEquals(RoutingDecision.NEED_CLARIFICATION,
                service.decide("帮我查一下。", "", failed).decision().getDecision());
    }
}
