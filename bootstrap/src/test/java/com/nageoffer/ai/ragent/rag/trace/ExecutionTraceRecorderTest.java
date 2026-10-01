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

package com.nageoffer.ai.ragent.rag.trace;

import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExecutionTraceRecorderTest {

    @AfterEach
    void clearContext() {
        RagTraceContext.clear();
    }

    @Test
    void writesStructuredRecordWithoutRuntimeProofMaterial() {
        RagTraceProperties properties = new RagTraceProperties();
        properties.setEnabled(true);
        RagTraceRecordService service = mock(RagTraceRecordService.class);
        ExecutionTraceRecorder recorder = new ExecutionTraceRecorder(properties, service);
        RagTraceContext.setTraceId("trace-1");

        ExecutionRecord record = new ExecutionRecord();
        record.addRoute(ExecutionRecord.RouteRecord.builder()
                .subQuestion("查询保修")
                .intent("WARRANTY_QUERY")
                .resource(ExecutionRecord.ResourceRecord.builder()
                        .type("MCP")
                        .name("getWarrantyStatus")
                        .build())
                .build());
        record.addToolObservation(null, ExecutionRecord.ToolExecutionRecord.builder()
                .tool("getWarrantyStatus")
                .arguments(Map.of("sn", "ABC001", "principalId", "U001"))
                .status(ExecutionRecord.ToolStatus.SUCCESS)
                .resultStatus("OK")
                .build());

        recorder.record(record);

        ArgumentCaptor<RagTraceNodeDO> captor = ArgumentCaptor.forClass(RagTraceNodeDO.class);
        verify(service).startNode(captor.capture());
        RagTraceNodeDO node = captor.getValue();
        assertThat(node.getNodeName()).isEqualTo("execution-record");
        assertThat(node.getNodeType()).isEqualTo("EXECUTION");
        assertThat(node.getExtraData())
                .contains("WARRANTY_QUERY", "getWarrantyStatus", "principalId", "U001")
                .doesNotContain("_runtimeProof", "_runtimeIssuedAt");
    }

    @Test
    void skipsEmptyRecord() {
        RagTraceProperties properties = new RagTraceProperties();
        RagTraceRecordService service = mock(RagTraceRecordService.class);
        ExecutionTraceRecorder recorder = new ExecutionTraceRecorder(properties, service);
        RagTraceContext.setTraceId("trace-2");

        recorder.record(new ExecutionRecord());

        verify(service, never()).startNode(org.mockito.ArgumentMatchers.any());
    }
}
