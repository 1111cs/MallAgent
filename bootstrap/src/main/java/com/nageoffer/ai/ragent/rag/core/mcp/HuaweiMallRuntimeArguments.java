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

package com.nageoffer.ai.ragent.rag.core.mcp;

import com.nageoffer.ai.ragent.framework.context.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Injects a trusted fixture principal after model extraction, without changing tool selection. */
@Component
public class HuaweiMallRuntimeArguments {
    private static final Set<String> PRIVATE = Set.of("getOrder","getLogistics","getCouponEligibility","getWarrantyStatus","getRepairProgress");
    private final Map<String,String> authMap;
    private final byte[] secret;

    public HuaweiMallRuntimeArguments(@Value("${huawei-mall.auth-map:}") String mapping,
                                       @Value("${huawei-mall.runtime-secret:}") String runtimeSecret) {
        this.authMap=parseMapping(mapping);
        this.secret=runtimeSecret==null?new byte[0]:runtimeSecret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isPrivate(String toolName) { return PRIVATE.contains(toolName); }

    public String resolvePrincipal(String authenticatedUserId) {
        if (authenticatedUserId == null) return null;
        String principal = authMap.get(authenticatedUserId);
        return principal != null && principal.matches("U[0-9]{3}") ? principal : null;
    }

    public Map<String,Object> bind(String toolName,Map<String,Object> modelArguments) {
        Map<String,Object> arguments = new LinkedHashMap<>(modelArguments==null?Map.of():modelArguments);
        if (!isPrivate(toolName)) return arguments;
        arguments.remove("userId"); arguments.remove("principalId"); arguments.remove("principal_id");
        arguments.remove("_runtimePrincipal"); arguments.remove("_runtimeIssuedAt"); arguments.remove("_runtimeProof");
        String loginId=UserContext.getUserId();
        String principal=authMap.get(loginId);
        if (loginId==null || principal==null || !principal.matches("U[0-9]{3}") || secret.length<32) {
            throw new IllegalStateException("Trusted Huawei Mall principal mapping or runtime secret is unavailable");
        }
        long issuedAt=Instant.now().getEpochSecond();
        arguments.put("_runtimePrincipal",principal);
        arguments.put("_runtimeIssuedAt",issuedAt);
        arguments.put("_runtimeProof",HexFormat.of().formatHex(hmac(toolName+"\n"+principal+"\n"+issuedAt)));
        return arguments;
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("HMAC unavailable",e); }
    }

    private static Map<String,String> parseMapping(String value) {
        Map<String,String> result=new LinkedHashMap<>();
        if(value==null || value.isBlank()) return result;
        for(String entry:value.split(",")) {
            String[] pair=entry.trim().split("=",2);
            if(pair.length!=2 || pair[0].isBlank() || !pair[1].matches("U[0-9]{3}")) {
                throw new IllegalArgumentException("Invalid Huawei Mall auth mapping");
            }
            result.put(pair[0],pair[1]);
        }
        return Map.copyOf(result);
    }
}
