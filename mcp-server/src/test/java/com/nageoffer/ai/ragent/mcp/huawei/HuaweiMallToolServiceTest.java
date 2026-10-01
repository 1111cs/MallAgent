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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct Tool contract checks against the fixed Mock Seed, without an LLM. */
@Tag("external")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HuaweiMallToolServiceTest {
    @Autowired HuaweiMallToolService service;

    @Test void fixedClock() {
        var range=HuaweiMallClock.lastMonth("LAST_MONTH");
        assertThat(range.startInclusive()).isEqualTo(Instant.parse("2026-07-31T16:00:00Z"));
        assertThat(range.endExclusive()).isEqualTo(Instant.parse("2026-08-31T16:00:00Z"));
        assertThat(HuaweiMallClock.iso(HuaweiMallClock.AS_OF.toInstant())).isEqualTo("2026-09-14T10:00:00+08:00");
    }

    @Test void searchProducts() {
        var ok=call("searchProducts",Map.of("category","laptop","budgetMax",7000,"usage",List.of("coding")),null);
        assertThat(ok.status()).isEqualTo("OK");
        assertThat(rows(ok,"products")).extracting(p -> p.get("productId")).contains("P-LAPTOP-002").doesNotContain("P-LAPTOP-001");
        assertThat(call("searchProducts",Map.of("category","phone","usage",List.of("coding")),null).error().get("code"))
                .isEqualTo("UNSUPPORTED_FILTER");
    }

    @Test void availability() {
        var out=call("getProductAvailability",Map.of("productId","P-LAPTOP-001","region","GUANGZHOU"),null);
        assertThat(out.status()).isEqualTo("OK");
        assertThat(out.data()).containsEntry("stock",0).containsEntry("stockStatus","OUT_OF_STOCK");
        assertThat(call("getProductAvailability",Map.of("productId","P-LAPTOP-001"),null).data())
                .containsEntry("region","CN_DEFAULT").containsEntry("stock",20);
    }

    @Test void order() {
        var amb=call("getOrder",Map.of("timeRange","LAST_MONTH","category","wearable"),"U002");
        assertThat(amb.status()).isEqualTo("MULTIPLE_MATCHES");
        assertThat(amb.candidates()).hasSize(2);
        var dep=call("getOrder",Map.of("timeRange","LAST_MONTH","category","wearable"),"U003");
        assertThat(dep.status()).isEqualTo("OK");
        assertThat(rows(dep,"orders")).hasSize(1);
        assertThat(call("getOrder",Map.of("orderNo","202609140001"),"U002").error().get("code"))
                .isEqualTo("ORDER_CONTEXT_MISMATCH");
        assertThat(call("getOrder",Map.of("userId","U001"),"U002").error().get("code"))
                .isEqualTo("PROTECTED_ARGUMENT");
        assertThat(call("getOrder",Map.of("timeRange","THIS_MONTH"),"U003").status())
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test void logistics() {
        var ok=call("getLogistics",Map.of("orderNo","202609140001"),"U001");
        assertThat(ok.status()).isEqualTo("OK");
        assertThat(ok.data()).containsEntry("trackingNo","SF202609140001");
        assertThat(rows(ok,"events")).hasSize(2);
        assertThat(call("getLogistics",Map.of("orderNo","202609140001"),"U003").error().get("code"))
                .isEqualTo("ORDER_CONTEXT_MISMATCH");
    }

    @Test void couponEligibility() {
        var eligible=call("getCouponEligibility",Map.of("couponId","C001","orderNo","202607050001"),"U002");
        assertThat(eligible.status()).isEqualTo("OK");
        assertThat(eligible.data()).containsEntry("eligible",true).containsEntry("reasonCode","ELIGIBLE");
        assertThat(call("getCouponEligibility",Map.of("couponId","C001","orderNo","202608100001"),"U002").data())
                .containsEntry("reasonCode","PRODUCT_NOT_SUPPORTED");
        assertThat(call("getCouponEligibility",Map.of("couponId","C004","productIds",List.of("P-PHONE-001")),"U003").data())
                .containsEntry("reasonCode","COUPON_ALREADY_USED");
        assertThat(call("getCouponEligibility",Map.of("couponId","C001"),"U002").status())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(call("getCouponEligibility",Map.of("couponId","C001","orderNo","202607050001","productIds",List.of("P-WATCH-001")),"U002").error().get("code"))
                .isEqualTo("ORDER_PRODUCTS_MISMATCH");
    }

    @Test void warranty() {
        var ok=call("getWarrantyStatus",Map.of("sn","WATCH-SN-003"),"U003");
        assertThat(ok.status()).isEqualTo("OK");
        assertThat(ok.data()).containsEntry("warrantyStatus","IN_WARRANTY");
        assertThat(call("getWarrantyStatus",Map.of("sn","WATCH-SN-003"),"U002").error().get("code"))
                .isEqualTo("DEVICE_CONTEXT_MISMATCH");
        assertThat(call("getWarrantyStatus",Map.of(),"U003").status()).isEqualTo("VALIDATION_ERROR");
    }

    @Test void sparePrice() {
        var normal=call("getSparePartPrice",Map.of("model","MateBook X Pro 2026","part","screen"),null);
        assertThat(normal.status()).isEqualTo("OK");
        assertThat(normal.data()).containsEntry("partPrice",new BigDecimal("2999.00"))
                .containsEntry("laborFee",new BigDecimal("300.00"))
                .containsEntry("estimatedTotal",new BigDecimal("3299.00"));
        assertThat(call("getSparePartPrice",Map.of("productId","P-WATCH-001","part","screen"),null).data())
                .containsEntry("estimatedTotal",new BigDecimal("599.00"));
        assertThat(call("getSparePartPrice",Map.of("productId","P-LAPTOP-001","model","MateBook 14 2026","part","screen"),null).error().get("code"))
                .isEqualTo("PRODUCT_MODEL_MISMATCH");
    }

    @Test void repairProgress() {
        var normal=call("getRepairProgress",Map.of("serviceNo","WX202609001"),"U001");
        assertThat(normal.status()).isEqualTo("OK");
        assertThat(normal.data()).containsEntry("repairStatus","REPAIRING").containsEntry("contactMasked","138****0001");
        assertThat(rows(normal,"progressEvents")).hasSize(3);
        assertThat(call("getRepairProgress",Map.of("phone","13800000001"),"U001").status())
                .isEqualTo("MULTIPLE_MATCHES");
        assertThat(call("getRepairProgress",Map.of("serviceNo","WX202609001"),"U002").error().get("code"))
                .isEqualTo("REPAIR_CONTEXT_MISMATCH");
    }

    @Test void fixedTimeBoundaryAndWarrantyStates() {
        var august=call("getOrder",Map.of("timeRange","LAST_MONTH","category","router"),"U004");
        assertThat(august.status()).isEqualTo("OK");
        assertThat(rows(august,"orders")).extracting(o -> o.get("orderNo")).containsExactly("202608010004");
        assertThat(call("getOrder",Map.of("orderNo","202609010004","timeRange","LAST_MONTH"),"U004").error().get("code"))
                .isEqualTo("ORDER_CONTEXT_MISMATCH");
        assertThat(call("getWarrantyStatus",Map.of("sn","ACCEPT-EXPIRED-001"),"U004").data())
                .containsEntry("warrantyStatus","OUT_OF_WARRANTY");
        assertThat(call("getWarrantyStatus",Map.of("sn","ACCEPT-UNKNOWN-001"),"U004").data())
                .containsEntry("warrantyStatus","UNKNOWN");
    }

    @Test void conditionsAndBusinessNoResultRemainDistinct() {
        assertThat(call("getSparePartPrice",Map.of("model","AX3","part","screen"),null).status())
                .isEqualTo("MULTIPLE_MATCHES");
        assertThat(call("getSparePartPrice",Map.of("productId","P-LAPTOP-001","model","MateBook X Pro 2026","part","screen"),null).status())
                .isEqualTo("OK");
        assertThat(call("getSparePartPrice",Map.of("productId","P-LAPTOP-001","part","screen","region","GUANGZHOU"),null).status())
                .isEqualTo("NOT_FOUND");
        assertThat(call("getCouponEligibility",Map.of("couponId","C001","orderNo","202607050001","productIds",List.of("P-LAPTOP-002")),"U002").status())
                .isEqualTo("OK");
        assertThat(call("getCouponEligibility",Map.of("couponId","C002","productIds",List.of("P-LAPTOP-003")),"U002").data())
                .containsEntry("reasonCode","MIN_AMOUNT_NOT_MET");
        assertThat(call("getCouponEligibility",Map.of("couponId","C003","orderNo","202607100001"),"U001").data())
                .containsEntry("reasonCode","EXPIRED");
        assertThat(call("getCouponEligibility",Map.of("couponId","C001","productIds",List.of("P-NOT-EXIST")),"U002").status())
                .isEqualTo("NOT_FOUND");
        assertThat(call("getRepairProgress",Map.of("serviceNo","WX202609001","phone","13800000001"),"U001").status())
                .isEqualTo("OK");
        assertThat(call("getRepairProgress",Map.of("serviceNo","WX202609001","phone","13900000002"),"U001").error().get("code"))
                .isEqualTo("REPAIR_CONTEXT_MISMATCH");
    }

    @Test void missingInvalidNoResultAndRepeatability() {
        assertThat(call("searchProducts",Map.of(),null).status()).isEqualTo("VALIDATION_ERROR");
        assertThat(call("searchProducts",Map.of("category","laptop","budgetMax",1),null).status()).isEqualTo("NOT_FOUND");
        assertThat(call("getProductAvailability",Map.of(),null).status()).isEqualTo("VALIDATION_ERROR");
        assertThat(call("getProductAvailability",Map.of("productId","P-LAPTOP-001","region","INVALID"),null).status())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(call("getProductAvailability",Map.of("productId","P-UNKNOWN"),null).status()).isEqualTo("NOT_FOUND");
        assertThat(call("getLogistics",Map.of(),"U001").status()).isEqualTo("VALIDATION_ERROR");
        assertThat(call("getLogistics",Map.of("orderNo","ORDER-UNKNOWN"),"U001").status()).isEqualTo("NOT_FOUND");
        assertThat(call("getCouponEligibility",Map.of("orderNo","202607050001"),"U002").status())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(call("getWarrantyStatus",Map.of("sn",123),"U003").status()).isEqualTo("VALIDATION_ERROR");
        assertThat(call("getSparePartPrice",Map.of("part","screen"),null).status()).isEqualTo("VALIDATION_ERROR");
        assertThat(call("getSparePartPrice",Map.of("productId","P-LAPTOP-001","part","INVALID"),null).status())
                .isEqualTo("VALIDATION_ERROR");
        assertThat(call("getRepairProgress",Map.of(),"U001").status()).isEqualTo("VALIDATION_ERROR");
        Map<String,Object> search=Map.of("category","laptop","budgetMax",7000);
        assertThat(call("searchProducts",search,null)).isEqualTo(call("searchProducts",search,null));
        Map<String,Object> order=Map.of("orderNo","202609140001");
        assertThat(call("getOrder",order,"U001")).isEqualTo(call("getOrder",order,"U001"));
    }

    private HuaweiMallToolService.Result call(String tool,Map<String,Object> args,String principal) {
        return service.execute(tool,args,principal);
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(HuaweiMallToolService.Result r,String field) {
        return (List<Map<String,Object>>)r.data().get(field);
    }
}
