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

package com.nageoffer.ai.ragent.rag.adapter.huawei;

import com.nageoffer.ai.ragent.rag.core.entity.EntityCandidate;
import com.nageoffer.ai.ragent.rag.core.entity.EntityCandidateProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JdbcHuaweiMallEntityCandidateProvider implements EntityCandidateProvider {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<EntityCandidate> findProducts() {
        return jdbcTemplate.query("""
                select id, product_name, model, category
                  from huawei_mall_mock.t_product
                 where status = 'ACTIVE'
                 order by id
                """, (rs, row) -> candidate(rs.getString("id"), rs.getString("product_name"), Map.of(
                "productId", rs.getString("id"), "model", rs.getString("model"),
                "category", rs.getString("category"))));
    }

    @Override
    public List<EntityCandidate> findOwnedDevices(String principalId) {
        return jdbcTemplate.query("""
                select d.id, d.sn, d.product_id, d.purchase_date,
                       p.product_name, p.model, p.category
                  from huawei_mall_mock.t_device d
                  join huawei_mall_mock.t_product p on p.id = d.product_id
                 where d.user_id = ?
                 order by d.purchase_date, d.id
                """, (rs, row) -> {
            Map<String, Object> bindings = new LinkedHashMap<>();
            bindings.put("sn", rs.getString("sn"));
            bindings.put("productId", rs.getString("product_id"));
            bindings.put("model", rs.getString("model"));
            bindings.put("category", rs.getString("category"));
            bindings.put("purchaseDate", rs.getDate("purchase_date").toLocalDate().toString());
            String label = rs.getString("product_name") + "（购买日期 " + bindings.get("purchaseDate") + "）";
            return candidate(rs.getString("id"), label, bindings);
        }, principalId);
    }

    @Override
    public List<EntityCandidate> findOwnedCoupons(String principalId) {
        return jdbcTemplate.query("""
                select uc.id, uc.coupon_id, uc.status, c.coupon_name, c.applicable_category
                  from huawei_mall_mock.t_user_coupon uc
                  join huawei_mall_mock.t_coupon c on c.id = uc.coupon_id
                 where uc.user_id = ?
                 order by uc.id
                """, (rs, row) -> candidate(rs.getString("id"),
                rs.getString("coupon_name") + "（" + rs.getString("status") + "）", Map.of(
                        "couponId", rs.getString("coupon_id"),
                        "couponName", rs.getString("coupon_name"),
                        "status", rs.getString("status"),
                        "category", String.valueOf(rs.getString("applicable_category")))), principalId);
    }

    private EntityCandidate candidate(String id, String label, Map<String, Object> bindings) {
        return new EntityCandidate(id, label, bindings);
    }
}
