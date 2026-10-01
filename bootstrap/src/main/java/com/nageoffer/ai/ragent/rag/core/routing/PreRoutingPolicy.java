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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PreRoutingPolicy {
    private final HuaweiMallRuntimeArguments runtimeArguments;

    public Optional<RoutingDecisionResult> decide(String query, String authenticatedUserId) {
        String value = normalize(query);
        if (isSelfIdentity(value)) {
            String principal = runtimeArguments.resolvePrincipal(authenticatedUserId);
            String response = principal == null
                    ? "当前登录身份未完成业务用户映射。"
                    : "您当前对应的业务用户编号是 " + principal + "。";
            return Optional.of(fixed(RoutingDecision.AUTH_CONTEXT,
                    RoutingReason.SELF_IDENTITY_QUERY, response));
        }
        if (isPureSystemMeta(value)) {
            return Optional.of(fixed(RoutingDecision.SYSTEM, RoutingReason.SYSTEM_META_QUERY,
                    "我是 Huawei Mall 业务助手，可以协助商品咨询、购物规则、订单与物流、售后服务和设备支持。"));
        }
        return Optional.empty();
    }

    private RoutingDecisionResult fixed(RoutingDecision decision, RoutingReason reason, String response) {
        return RoutingDecisionResult.builder().decision(decision).reason(reason).fixedResponse(response).build();
    }

    private boolean isSelfIdentity(String value) {
        return value.matches("^(我的|当前|我现在)?(用户编号|业务用户编号)(是|是什么|是多少)?$")
                || value.matches("^(我当前|我现在)?(对应|登录的)?(哪个|什么)?业务用户(是哪个|是谁)?$")
                || value.matches("^我现在登录的是谁$");
    }

    private boolean isPureSystemMeta(String value) {
        if (containsBusinessAction(value)) return false;
        return value.matches("^(你是谁|你是什么|你能做什么|你可以做什么|你支持什么|你支持哪些功能|你支持哪些业务|你的能力范围是什么|你有哪些能力|你能帮我处理哪些商城问题)$");
    }

    private boolean containsBusinessAction(String value) {
        return value.matches(".*(订单|物流|商品|保修|维修|优惠券|库存|价格|发票|退货|换货|连接|充电).*" );
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、]+", "");
    }
}

