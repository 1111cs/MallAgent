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

package com.nageoffer.ai.ragent.mcp.huawei;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Calls all MCP Tool handlers directly; verifies hidden identity and dual JSON output. */
@Tag("external")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties="huawei-mall.runtime-secret=0123456789abcdef0123456789abcdef")
class HuaweiMallMcpExecutorTest {
    private static final String SECRET="0123456789abcdef0123456789abcdef";
    @Autowired HuaweiMallMcpExecutor executor;
    @Autowired ObjectMapper mapper;

    @Test void allEightToolHandlersReturnTheFrozenEnvelopeAndStructuredContent() throws Exception {
        record Input(McpServerFeatures.SyncToolSpecification spec,Map<String,Object> args,String principal) { }
        List<Input> inputs=List.of(
                new Input(executor.searchProductsTool(),Map.of("category","laptop","budgetMax",7000),null),
                new Input(executor.availabilityTool(),Map.of("productId","P-LAPTOP-001"),null),
                new Input(executor.orderTool(),Map.of("orderNo","202609140001"),"U001"),
                new Input(executor.logisticsTool(),Map.of("orderNo","202609140001"),"U001"),
                new Input(executor.couponTool(),Map.of("couponId","C001","orderNo","202607050001"),"U002"),
                new Input(executor.warrantyTool(),Map.of("sn","WATCH-SN-003"),"U003"),
                new Input(executor.spareTool(),Map.of("model","MateBook X Pro 2026","part","screen"),null),
                new Input(executor.repairTool(),Map.of("serviceNo","WX202609001"),"U001"));
        assertThat(inputs).hasSize(8);
        for (Input input:inputs) {
            assertThat(input.spec.tool().inputSchema().properties()).doesNotContainKeys("userId","principalId","_runtimeProof");
            CallToolResult output=call(input.spec,input.args,input.principal);
            assertThat(output.isError()).isFalse();
            assertThat(output.structuredContent()).isNotNull();
            String text=((TextContent)output.content().get(0)).text();
            var parsed=mapper.readTree(text);
            assertThat(parsed.path("status").asText()).isEqualTo("OK");
            assertThat(parsed.path("meta").path("asOf").asText()).isEqualTo("2026-09-14T10:00:00+08:00");
            assertThat(mapper.readTree(mapper.writeValueAsString(output.structuredContent()))).isEqualTo(parsed);
        }
    }

    @Test void privateToolRejectsMissingOrForgedRuntimeIdentity() throws Exception {
        var spec=executor.orderTool();
        CallToolResult missing=spec.callHandler().apply(null,new CallToolRequest("getOrder",Map.of("orderNo","202609140001")));
        assertThat(missing.isError()).isTrue();
        assertThat(mapper.readTree(((TextContent)missing.content().get(0)).text()).path("error").path("code").asText())
                .isEqualTo("AUTH_CONTEXT_MISSING");
        Map<String,Object> forged=new LinkedHashMap<>(Map.of("orderNo","202609140001"));
        forged.put("_runtimePrincipal","U002"); forged.put("_runtimeIssuedAt",Instant.now().getEpochSecond());
        forged.put("_runtimeProof","00");
        assertThat(spec.callHandler().apply(null,new CallToolRequest("getOrder",forged)).isError()).isTrue();
    }

    private CallToolResult call(McpServerFeatures.SyncToolSpecification spec,Map<String,Object> modelArgs,String principal) throws Exception {
        Map<String,Object> arguments=new LinkedHashMap<>(modelArgs);
        if (principal!=null) {
            long now=Instant.now().getEpochSecond();
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            String signature=HexFormat.of().formatHex(mac.doFinal((spec.tool().name()+"\n"+principal+"\n"+now).getBytes(StandardCharsets.UTF_8)));
            arguments.put("_runtimePrincipal",principal);
            arguments.put("_runtimeIssuedAt",now);
            arguments.put("_runtimeProof",signature);
        }
        return spec.callHandler().apply(null,new CallToolRequest(spec.tool().name(),arguments));
    }
}
