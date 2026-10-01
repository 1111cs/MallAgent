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

package com.nageoffer.ai.ragent.rag.core.clarification;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ParameterDescriptorRegistry {
    public record ParameterDescriptor(String displayName, String askPhrase) {}

    private static final Map<String, ParameterDescriptor> DESCRIPTORS = Map.ofEntries(
            Map.entry("sn", new ParameterDescriptor("设备 SN", "请提供设备 SN")),
            Map.entry("serviceNo", new ParameterDescriptor("维修服务单号", "请提供维修服务单号")),
            Map.entry("phone", new ParameterDescriptor("预约手机号", "请提供预约时填写的手机号")),
            Map.entry("couponId", new ParameterDescriptor("优惠券", "请说明具体是哪张优惠券")),
            Map.entry("orderNo", new ParameterDescriptor("订单号", "请提供订单号")),
            Map.entry("productIds", new ParameterDescriptor("商品", "请提供商品")),
            Map.entry("productId", new ParameterDescriptor("商品", "请提供具体商品或商品 ID")),
            Map.entry("model", new ParameterDescriptor("型号", "请提供具体型号")),
            Map.entry("region", new ParameterDescriptor("所在地区", "请提供所在地区")),
            Map.entry("part", new ParameterDescriptor("维修部件", "请说明需要查询的维修部件")),
            Map.entry("category", new ParameterDescriptor("商品类别", "请说明商品类别"))
    );

    public ParameterDescriptor get(String key) {
        return DESCRIPTORS.getOrDefault(key, new ParameterDescriptor(key, "请提供" + key));
    }
}
