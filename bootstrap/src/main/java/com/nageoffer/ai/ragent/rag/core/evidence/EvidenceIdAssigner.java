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

package com.nageoffer.ai.ragent.rag.core.evidence;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class EvidenceIdAssigner {
    public void assign(List<EvidenceRecord> evidence) {
        int order = 0;
        for (EvidenceRecord item : evidence) {
            if (item.isSelectedForPrompt()) {
                item.setPromptOrder(++order);
                item.setEvidenceId("E" + order);
            } else {
                item.setPromptOrder(null);
                item.setEvidenceId(null);
            }
        }
    }
}
