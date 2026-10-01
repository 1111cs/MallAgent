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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.core.clarification.PendingClarificationState;
import com.nageoffer.ai.ragent.rag.core.clarification.PendingReason;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PendingSelectionSerializationTest {
    @Test void roundTripsCandidateSnapshot() throws Exception {
        var state = PendingClarificationState.builder().pendingTaskId("PT-1")
                .authenticatedUserId("login").conversationId("C1")
                .pendingReason(PendingReason.NEED_SELECTION).selectionTarget("sn").entityType("DEVICE")
                .candidates(List.of(new EntityCandidate("D1", "设备一", Map.of("sn", "S1")))).build();
        ObjectMapper mapper = new ObjectMapper();
        var restored = mapper.readValue(mapper.writeValueAsString(state), PendingClarificationState.class);
        assertEquals(PendingReason.NEED_SELECTION, restored.getPendingReason());
        assertEquals("S1", restored.getCandidates().get(0).bindings().get("sn"));
    }
}
