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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceNodeDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;

/** Writes one compact decision-boundary node for a chat request. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionTraceRecorder {

    static final String NODE_NAME = "execution-record";
    static final String NODE_TYPE = "EXECUTION";

    private final RagTraceProperties traceProperties;
    private final RagTraceRecordService traceRecordService;

    public void record(ExecutionRecord executionRecord) {
        if (!traceProperties.isEnabled() || executionRecord == null || executionRecord.isEmpty()) {
            return;
        }
        String traceId = RagTraceContext.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            return;
        }
        try {
            Date now = new Date();
            traceRecordService.startNode(RagTraceNodeDO.builder()
                    .traceId(traceId)
                    .nodeId(IdUtil.getSnowflakeNextIdStr())
                    .parentNodeId(RagTraceContext.currentNodeId())
                    .depth(RagTraceContext.depth())
                    .nodeType(NODE_TYPE)
                    .nodeName(NODE_NAME)
                    .className(ExecutionTraceRecorder.class.getName())
                    .methodName("record")
                    .status("SUCCESS")
                    .startTime(now)
                    .endTime(now)
                    .durationMs(0L)
                    .extraData(JSONUtil.toJsonStr(executionRecord))
                    .build());
        } catch (Exception e) {
            // Observability must not change the chat result.
            log.warn("execution record 写入失败，traceId：{}", traceId, e);
        }
    }
}
