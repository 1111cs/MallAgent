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
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Eight read-only MCP definitions; protected identity fields are absent from model schemas. */
@Slf4j
@Component
public class HuaweiMallMcpExecutor {
    private static final Set<String> PRIVATE = Set.of("getOrder","getLogistics","getCouponEligibility","getWarrantyStatus","getRepairProgress");
    private static final List<String> CATEGORY = List.of("phone","tablet","laptop","wearable","headphone","router");
    private static final List<String> REGION = List.of("CN_DEFAULT","BEIJING","SHANGHAI","GUANGZHOU");
    private static final List<String> PART = List.of("screen","battery","mainboard","back_cover","camera","other");
    private final HuaweiMallToolService service;
    private final ObjectMapper mapper;
    private final HuaweiMallRuntimeProof proof;

    public HuaweiMallMcpExecutor(HuaweiMallToolService service, ObjectMapper mapper,
                                  @Value("${huawei-mall.runtime-secret:}") String runtimeSecret) {
        this.service=service; this.mapper=mapper; this.proof=new HuaweiMallRuntimeProof(runtimeSecret);
    }

    @Bean public McpServerFeatures.SyncToolSpecification searchProductsTool() {
        return spec("searchProducts","Search active Huawei Mall products by category, budget and supported laptop needs",
                props("category",p("string","Product category",CATEGORY),
                      "budgetMin",p("number","Minimum CNY budget"),"budgetMax",p("number","Maximum CNY budget"),
                      "usage",array("Laptop usage: coding, office, business_travel, video_editing, study"),
                      "requirements",array("Laptop needs: lightweight, long_battery, high_memory, large_screen, portable")),List.of("category"));
    }
    @Bean public McpServerFeatures.SyncToolSpecification availabilityTool() {
        return spec("getProductAvailability","Check fixed stock and price of an exact product in a region",
                props("productId",p("string","Exact Huawei Mall product ID"),"region",p("string","Region; default CN_DEFAULT",REGION)),List.of("productId"));
    }
    @Bean public McpServerFeatures.SyncToolSpecification orderTool() {
        return spec("getOrder","Query the authenticated principal's orders; LAST_MONTH is the only relative time range",
                props("orderNo",p("string","Exact order number"),"timeRange",p("string","Relative time range",List.of("LAST_MONTH")),
                      "category",p("string","Product category",CATEGORY),"productKeyword",p("string","Product keyword")),List.of());
    }
    @Bean public McpServerFeatures.SyncToolSpecification logisticsTool() {
        return spec("getLogistics","Query logistics for an exact order number owned by the authenticated principal",
                props("orderNo",p("string","Exact order number")),List.of("orderNo"));
    }
    @Bean public McpServerFeatures.SyncToolSpecification couponTool() {
        return spec("getCouponEligibility","Check whether an owned coupon applies to an order or selected product IDs",
                props("couponId",p("string","Exact coupon ID"),"orderNo",p("string","Exact order number; supply this or productIds"),
                      "productIds",array("Non-empty product IDs; supply this or orderNo")),List.of("couponId"));
    }
    @Bean public McpServerFeatures.SyncToolSpecification warrantyTool() {
        return spec("getWarrantyStatus","Query warranty of one owned device by its exact serial number",
                props("sn",p("string","Exact device SN")),List.of("sn"));
    }
    @Bean public McpServerFeatures.SyncToolSpecification spareTool() {
        return spec("getSparePartPrice","Get fixed current part and labor prices for an exact product or model",
                props("productId",p("string","Product ID; supply this or model"),"model",p("string","Exact model; supply this or productId"),
                      "part",p("string","Spare part",PART),"region",p("string","Region; default CN_DEFAULT",REGION)),List.of("part"));
    }
    @Bean public McpServerFeatures.SyncToolSpecification repairTool() {
        return spec("getRepairProgress","Get repair progress owned by the authenticated principal using service number or phone",
                props("serviceNo",p("string","Exact service number; supply this or phone"),
                      "phone",p("string","Chinese mainland phone; supply this or serviceNo")),List.of());
    }

    private McpServerFeatures.SyncToolSpecification spec(String name,String description,Map<String,Object> properties,List<String> required) {
        Tool tool = Tool.builder().name(name).description(description)
                .inputSchema(new JsonSchema("object",properties,required,null,null,null)).build();
        return new McpServerFeatures.SyncToolSpecification(tool,(exchange,request) -> call(name,request));
    }

    private CallToolResult call(String name,CallToolRequest request) {
        Map<String,Object> raw = request.arguments()==null?Map.of():request.arguments();
        String principal=null;
        if (PRIVATE.contains(name)) {
            try { principal=proof.requirePrincipal(name,raw); }
            catch (IllegalStateException unavailable) {
                return response(service.runtimeIdentityUnavailable());
            } catch (IllegalArgumentException untrusted) {
                return response(service.execute(name,Map.of(),null));
            }
        }
        Map<String,Object> modelArgs = new LinkedHashMap<>(raw);
        modelArgs.remove("_runtimePrincipal"); modelArgs.remove("_runtimeIssuedAt"); modelArgs.remove("_runtimeProof");
        HuaweiMallToolService.Result result = service.execute(name,modelArgs,principal);
        return response(result);
    }

    private CallToolResult response(HuaweiMallToolService.Result result) {
        try {
            String json = mapper.writeValueAsString(result);
            // Same-request, machine-readable observation; it never changes execution decisions.
            log.info("HUAWEI_MALL_TOOL_EVENT {}",json);
            return CallToolResult.builder().content(List.of(new TextContent(json)))
                    .structuredContent(result).isError(result.mcpIsError()).build();
        } catch (Exception e) {
            return CallToolResult.builder().content(List.of(new TextContent("{\"status\":\"INTERNAL_ERROR\"}"))).isError(true).build();
        }
    }

    private static Map<String,Object> p(String type,String description) { return Map.of("type",type,"description",description); }
    private static Map<String,Object> p(String type,String description,List<String> values) { return Map.of("type",type,"description",description,"enum",values); }
    private static Map<String,Object> array(String description) { return Map.of("type","array","description",description,"items",Map.of("type","string")); }
    private static Map<String,Object> props(Object... values) {
        Map<String,Object> map = new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2) map.put((String)values[i],values[i+1]);
        return map;
    }
}
