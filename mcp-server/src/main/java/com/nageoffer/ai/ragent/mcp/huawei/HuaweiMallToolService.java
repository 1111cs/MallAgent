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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic, read-only Huawei Mall queries. Principal is a separate trusted argument. */
@Service
public class HuaweiMallToolService {
    private static final Set<String> CATEGORIES = Set.of("phone","tablet","laptop","wearable","headphone","router");
    private static final Set<String> REGIONS = Set.of("CN_DEFAULT","BEIJING","SHANGHAI","GUANGZHOU");
    private static final Set<String> PARTS = Set.of("screen","battery","mainboard","back_cover","camera","other");
    private static final Set<String> USAGE = Set.of("coding","office","business_travel","video_editing","study");
    private static final Set<String> REQUIREMENTS = Set.of("lightweight","long_battery","high_memory","large_screen","portable");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String datasetVersion;

    public HuaweiMallToolService(JdbcTemplate jdbc, ObjectMapper mapper,
                                 @Value("${huawei-mall.dataset-version:huawei-mall-fixture-v1.0}") String datasetVersion) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.datasetVersion = datasetVersion;
    }

    public Result execute(String tool, Map<String, Object> modelArguments, String trustedPrincipal) {
        Map<String, Object> args = modelArguments == null ? Map.of() : modelArguments;
        try {
            if (args.containsKey("userId") || args.containsKey("principalId") || args.containsKey("principal_id")) {
                throw invalid("PROTECTED_ARGUMENT", "userId", "Identity cannot be supplied as a model argument");
            }
            if (Set.of("getOrder","getLogistics","getCouponEligibility","getWarrantyStatus","getRepairProgress").contains(tool)
                    && (trustedPrincipal == null || !trustedPrincipal.matches("U[0-9]{3}"))) {
                throw invalid("AUTH_CONTEXT_MISSING", "userId", "Trusted principal is required");
            }
            return switch (tool) {
                case "searchProducts" -> searchProducts(args);
                case "getProductAvailability" -> getProductAvailability(args);
                case "getOrder" -> getOrder(args, trustedPrincipal);
                case "getLogistics" -> getLogistics(args, trustedPrincipal);
                case "getCouponEligibility" -> getCouponEligibility(args, trustedPrincipal);
                case "getWarrantyStatus" -> getWarrantyStatus(args, trustedPrincipal);
                case "getSparePartPrice" -> getSparePartPrice(args);
                case "getRepairProgress" -> getRepairProgress(args, trustedPrincipal);
                default -> throw invalid("UNKNOWN_TOOL", "tool", "Unknown Huawei Mall tool");
            };
        } catch (ToolProblem problem) {
            return result(problem.status, Map.of(), List.of(),
                    Map.of("code",problem.code,"message",problem.getMessage(),
                           "fieldErrors",List.of(Map.of("field",problem.field,"message",problem.getMessage()))));
        } catch (Exception exception) {
            return result("INTERNAL_ERROR", Map.of(), List.of(),
                    Map.of("code","TOOL_INTERNAL_ERROR","message","Huawei Mall query failed"));
        }
    }

    public Result runtimeIdentityUnavailable() {
        return result("INTERNAL_ERROR",Map.of(),List.of(),
                Map.of("code","AUTH_MAPPING_UNAVAILABLE","message","Trusted Huawei Mall runtime identity is not configured"));
    }

    private Result searchProducts(Map<String,Object> args) {
        String category = required(args,"category",64);
        oneOf(category,CATEGORIES,"category");
        BigDecimal min = decimal(args,"budgetMin");
        BigDecimal max = decimal(args,"budgetMax");
        if (min != null && min.signum()<0 || max != null && max.signum()<0 ||
                min != null && max != null && min.compareTo(max)>0) {
            throw invalid("INVALID_BUDGET","budgetMin","Budget must be non-negative and ordered");
        }
        List<String> usage = stringArray(args,"usage",USAGE);
        List<String> requirements = stringArray(args,"requirements",REQUIREMENTS);
        if (!category.equals("laptop") && (!usage.isEmpty() || !requirements.isEmpty())) {
            throw invalid("UNSUPPORTED_FILTER","usage","Usage filters are supported for laptops only");
        }
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM huawei_mall_mock.t_product WHERE category=? AND status='ACTIVE' ORDER BY current_price,id",category);
        List<Map<String,Object>> products = new ArrayList<>();
        for (Map<String,Object> row:rows) {
            BigDecimal price = money(row.get("current_price"));
            if (min != null && price.compareTo(min)<0 || max != null && price.compareTo(max)>0) continue;
            Map<String,Object> specs = spec(row.get("spec_json"));
            if (!specList(specs,"usage").containsAll(usage) || !specList(specs,"requirements").containsAll(requirements)) continue;
            List<String> matched = new ArrayList<>();
            matched.add("category="+category);
            if (min != null) matched.add("budgetMin="+min);
            if (max != null) matched.add("budgetMax="+max);
            usage.forEach(v -> matched.add("usage="+v));
            requirements.forEach(v -> matched.add("requirements="+v));
            products.add(map("productId",row.get("id"),"productName",row.get("product_name"),"category",category,
                    "series",row.get("series"),"model",row.get("model"),"currentPrice",price,"currency","CNY",
                    "keySpecs",specs,"matchedConstraints",matched));
        }
        return products.isEmpty() ? notFound() : ok(Map.of("products",products));
    }

    private Result getProductAvailability(Map<String,Object> args) {
        String id = required(args,"productId",64);
        String region = optional(args,"region",32,"CN_DEFAULT");
        oneOf(region,REGIONS,"region");
        Map<String,Object> p = first("SELECT * FROM huawei_mall_mock.t_product WHERE id=?",id);
        Map<String,Object> i = first("SELECT * FROM huawei_mall_mock.t_inventory WHERE product_id=? AND region=?",id,region);
        if (p == null || i == null) return notFound();
        int stock = ((Number)i.get("stock")).intValue();
        return ok(map("productId",id,"productName",p.get("product_name"),"region",region,
                "currentPrice",money(p.get("current_price")),"originalPrice",money(p.get("original_price")),
                "stock",stock,"stockStatus",stock>0?"IN_STOCK":"OUT_OF_STOCK",
                "promotionSummary",i.get("promotion_text"),"updatedAt",iso(i.get("updated_at")),"currency","CNY"));
    }

    private Result getOrder(Map<String,Object> args, String principal) {
        String orderNo = optional(args,"orderNo",64,null);
        String timeRange = optional(args,"timeRange",32,null);
        String category = optional(args,"category",32,null);
        String keyword = optional(args,"productKeyword",100,null);
        if (category != null) oneOf(category,CATEGORIES,"category");
        HuaweiMallClock.Range range = timeRange == null ? null : validRange(timeRange);
        if (orderNo != null) {
            Map<String,Object> row = first("SELECT * FROM huawei_mall_mock.t_order WHERE order_no=?",orderNo);
            if (row == null) return notFound();
            if (!principal.equals(row.get("user_id"))) return conflict("ORDER_CONTEXT_MISMATCH","orderNo","Order does not belong to the principal");
            Map<String,Object> order = orderData(row);
            if (!matchesOrder(row,order,range,category,keyword)) return conflict("ORDER_CONTEXT_MISMATCH","orderNo","Order conflicts with the supplied filters");
            return ok(Map.of("orders",List.of(order)));
        }
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM huawei_mall_mock.t_order WHERE user_id=? ORDER BY created_at DESC,id",principal);
        List<Map<String,Object>> orders = new ArrayList<>();
        for (Map<String,Object> row:rows) {
            Map<String,Object> order = orderData(row);
            if (matchesOrder(row,order,range,category,keyword)) orders.add(order);
        }
        if (orders.isEmpty()) return notFound();
        if (orders.size()>1) return result("MULTIPLE_MATCHES",Map.of(),orders,null);
        return ok(Map.of("orders",orders));
    }

    private boolean matchesOrder(Map<String,Object> raw, Map<String,Object> order, HuaweiMallClock.Range range,
                                 String category, String keyword) {
        Instant created = instant(raw.get("created_at"));
        if (range != null && (created.isBefore(range.startInclusive()) || !created.isBefore(range.endExclusive()))) return false;
        @SuppressWarnings("unchecked") List<Map<String,Object>> products = (List<Map<String,Object>>)order.get("products");
        if (category != null && products.stream().noneMatch(p -> category.equals(p.get("category")))) return false;
        return keyword == null || products.stream().anyMatch(p -> String.valueOf(p.get("productName")).toLowerCase().contains(keyword.toLowerCase()));
    }

    private Map<String,Object> orderData(Map<String,Object> raw) {
        List<Map<String,Object>> items = jdbc.queryForList(
                "SELECT oi.*,p.category FROM huawei_mall_mock.t_order_item oi JOIN huawei_mall_mock.t_product p ON p.id=oi.product_id WHERE oi.order_id=? ORDER BY oi.id",raw.get("id"));
        List<Map<String,Object>> products = new ArrayList<>();
        for (Map<String,Object> item:items) {
            products.add(map("productId",item.get("product_id"),"productName",item.get("product_name_snapshot"),
                    "category",item.get("category"),"model",item.get("model_snapshot"),
                    "sn",item.get("device_sn"),"quantity",item.get("quantity")));
        }
        return map("orderNo",raw.get("order_no"),"orderStatus",raw.get("status"),
                "createTime",iso(raw.get("created_at")),"totalAmount",money(raw.get("total_amount")),
                "currency","CNY","products",products,"deliveryAddressMasked",raw.get("receiver_address_masked"));
    }

    private Result getLogistics(Map<String,Object> args, String principal) {
        String orderNo = required(args,"orderNo",64);
        Map<String,Object> o = first("SELECT * FROM huawei_mall_mock.t_order WHERE order_no=?",orderNo);
        if (o == null) return notFound();
        if (!principal.equals(o.get("user_id"))) return conflict("ORDER_CONTEXT_MISMATCH","orderNo","Order does not belong to the principal");
        if (o.get("logistics_tracking_no") == null) return notFound();
        List<Map<String,Object>> events = new ArrayList<>();
        for (Map<String,Object> e:jdbc.queryForList("SELECT * FROM huawei_mall_mock.t_logistics_event WHERE order_id=? ORDER BY event_time,id",o.get("id"))) {
            events.add(map("time",iso(e.get("event_time")),"location",e.get("location"),"description",e.get("description")));
        }
        return ok(map("orderNo",orderNo,"carrier",o.get("logistics_carrier"),
                "trackingNo",o.get("logistics_tracking_no"),"status",o.get("logistics_status"),
                "estimatedDeliveryTime",iso(o.get("estimated_delivery_time")),"events",events));
    }

    private Result getCouponEligibility(Map<String,Object> args, String principal) {
        String couponId = required(args,"couponId",64);
        String orderNo = optional(args,"orderNo",64,null);
        List<String> productIds = stringArray(args,"productIds",null);
        if (orderNo == null && productIds.isEmpty()) throw invalid("MISSING_USAGE_OBJECT","orderNo","orderNo or productIds is required");
        Map<String,Object> coupon = first("SELECT * FROM huawei_mall_mock.t_coupon WHERE id=?",couponId);
        if (coupon == null) return notFound();
        for (String id:productIds) {
            if (first("SELECT id FROM huawei_mall_mock.t_product WHERE id=?",id) == null) return notFound();
        }
        Map<String,Object> order = orderNo == null ? null : first("SELECT * FROM huawei_mall_mock.t_order WHERE order_no=?",orderNo);
        if (orderNo != null && order == null) return notFound();
        if (order != null && !principal.equals(order.get("user_id"))) return conflict("ORDER_CONTEXT_MISMATCH","orderNo","Order does not belong to the principal");
        List<Map<String,Object>> items = order == null ? List.of() : jdbc.queryForList(
                "SELECT oi.product_id,oi.price,oi.quantity,p.category FROM huawei_mall_mock.t_order_item oi JOIN huawei_mall_mock.t_product p ON p.id=oi.product_id WHERE oi.order_id=?",order.get("id"));
        if (order != null && !productIds.isEmpty()) {
            Set<String> owned = new LinkedHashSet<>();
            items.forEach(i -> owned.add(String.valueOf(i.get("product_id"))));
            if (!owned.containsAll(productIds)) return conflict("ORDER_PRODUCTS_MISMATCH","productIds","Products do not belong to the order");
        }
        List<Map<String,Object>> targets = new ArrayList<>();
        if (order != null) {
            for (Map<String,Object> item:items) {
                if (productIds.isEmpty() || productIds.contains(item.get("product_id"))) targets.add(item);
            }
        } else {
            for (String id:productIds) {
                Map<String,Object> p = first("SELECT id AS product_id,current_price AS price,category FROM huawei_mall_mock.t_product WHERE id=?",id);
                if (p == null) return notFound();
                p.put("quantity",1);
                targets.add(p);
            }
        }
        Map<String,Object> ownedCoupon = first("SELECT * FROM huawei_mall_mock.t_user_coupon WHERE user_id=? AND coupon_id=?",principal,couponId);
        String reason;
        if (ownedCoupon == null) reason="USER_NOT_ELIGIBLE";
        else if ("USED".equals(ownedCoupon.get("status"))) reason="COUPON_ALREADY_USED";
        else if (!"ACTIVE".equals(coupon.get("status")) || HuaweiMallClock.AS_OF.toInstant().isBefore(instant(coupon.get("start_time")))
                || !HuaweiMallClock.AS_OF.toInstant().isBefore(instant(coupon.get("end_time")))) reason="EXPIRED";
        else {
            String category = (String)coupon.get("applicable_category");
            List<Map<String,Object>> supported = targets.stream().filter(t -> category == null || category.equals(t.get("category"))).toList();
            if (supported.isEmpty()) reason="PRODUCT_NOT_SUPPORTED";
            else {
                BigDecimal amount = supported.stream().map(t -> money(t.get("price")).multiply(new BigDecimal(String.valueOf(t.get("quantity")))))
                        .reduce(BigDecimal.ZERO,BigDecimal::add);
                reason = amount.compareTo(money(coupon.get("min_amount")))<0 ? "MIN_AMOUNT_NOT_MET" : "ELIGIBLE";
            }
        }
        return ok(map("eligible","ELIGIBLE".equals(reason),"couponId",couponId,"reasonCode",reason,
                "reason",reasonText(reason),"matchedRules",List.of(
                        "minAmount="+money(coupon.get("min_amount")),
                        "category="+String.valueOf(coupon.get("applicable_category")),
                        "endTime="+iso(coupon.get("end_time")))));
    }

    private Result getWarrantyStatus(Map<String,Object> args, String principal) {
        String sn = required(args,"sn",64);
        Map<String,Object> d = first("SELECT * FROM huawei_mall_mock.t_device WHERE sn=?",sn);
        if (d == null) return notFound();
        if (!principal.equals(d.get("user_id"))) return conflict("DEVICE_CONTEXT_MISMATCH","sn","Device does not belong to the principal");
        Map<String,Object> p = first("SELECT * FROM huawei_mall_mock.t_product WHERE id=?",d.get("product_id"));
        LocalDate start = date(d.get("warranty_start")), end = date(d.get("warranty_end"));
        LocalDate today = LocalDate.now(HuaweiMallClock.FIXED);
        String status = start == null || end == null ? "UNKNOWN" :
                (!today.isBefore(start) && !today.isAfter(end) ? "IN_WARRANTY":"OUT_OF_WARRANTY");
        return ok(map("sn",sn,"productId",d.get("product_id"),"productName",p.get("product_name"),"model",p.get("model"),
                "warrantyStatus",status,"warrantyStart",start,"warrantyEnd",end,"warrantyType",d.get("warranty_type")));
    }

    private Result getSparePartPrice(Map<String,Object> args) {
        String id = optional(args,"productId",64,null);
        String model = optional(args,"model",150,null);
        String part = required(args,"part",32);
        String region = optional(args,"region",32,"CN_DEFAULT");
        if (id == null && model == null) throw invalid("MISSING_PRODUCT","productId","productId or model is required");
        oneOf(part,PARTS,"part"); oneOf(region,REGIONS,"region");
        Map<String,Object> product;
        if (id != null) {
            product = first("SELECT * FROM huawei_mall_mock.t_product WHERE id=?",id);
            if (product == null) return notFound();
            if (model != null && !model.equals(product.get("model"))) return conflict("PRODUCT_MODEL_MISMATCH","model","Product and model conflict");
        } else {
            List<Map<String,Object>> matches = jdbc.queryForList("SELECT * FROM huawei_mall_mock.t_product WHERE model=? ORDER BY id",model);
            if (matches.isEmpty()) return notFound();
            if (matches.size()>1) {
                List<Map<String,Object>> candidates = matches.stream().map(p -> map("productId",p.get("id"),"productName",p.get("product_name"),"model",p.get("model"))).toList();
                return result("MULTIPLE_MATCHES",Map.of(),candidates,null);
            }
            product = matches.get(0);
            id = String.valueOf(product.get("id"));
        }
        Map<String,Object> price = first("SELECT * FROM huawei_mall_mock.t_spare_part_price WHERE product_id=? AND part_code=? AND region=? AND effective_at<=? ORDER BY effective_at DESC,id DESC LIMIT 1",
                id,part,region,Timestamp.from(HuaweiMallClock.AS_OF.toInstant()));
        if (price == null) return notFound();
        BigDecimal partPrice = money(price.get("part_price")), labor = money(price.get("labor_fee"));
        return ok(map("productId",id,"productName",product.get("product_name"),"model",product.get("model"),
                "part",part,"partName",price.get("part_name"),"partPrice",partPrice,"laborFee",labor,
                "estimatedTotal",partPrice.add(labor),"currency","CNY","priceNote",price.get("price_note"),
                "updatedAt",iso(price.get("updated_at")),"region",region));
    }

    private Result getRepairProgress(Map<String,Object> args, String principal) {
        String serviceNo = optional(args,"serviceNo",64,null);
        String phone = optional(args,"phone",20,null);
        if (serviceNo == null && phone == null) throw invalid("MISSING_REPAIR_SELECTOR","serviceNo","serviceNo or phone is required");
        if (phone != null && !phone.matches("^1[3-9][0-9]{9}$")) throw invalid("INVALID_PHONE","phone","Phone format is invalid");
        if (serviceNo != null) {
            Map<String,Object> r = first("SELECT * FROM huawei_mall_mock.t_repair_order WHERE service_no=?",serviceNo);
            if (r == null) return notFound();
            if (!principal.equals(r.get("user_id"))) return conflict("REPAIR_CONTEXT_MISMATCH","serviceNo","Repair does not belong to the principal");
            if (phone != null && !phone.equals(r.get("contact_phone"))) return conflict("REPAIR_CONTEXT_MISMATCH","phone","Phone conflicts with the repair");
            return ok(repairData(r));
        }
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM huawei_mall_mock.t_repair_order WHERE user_id=? AND contact_phone=? ORDER BY created_at DESC,id",principal,phone);
        if (rows.isEmpty()) return notFound();
        if (rows.size()>1) return result("MULTIPLE_MATCHES",Map.of(),rows.stream().map(this::repairData).toList(),null);
        return ok(repairData(rows.get(0)));
    }

    private Map<String,Object> repairData(Map<String,Object> r) {
        Map<String,Object> p = first("SELECT p.product_name FROM huawei_mall_mock.t_device d JOIN huawei_mall_mock.t_product p ON p.id=d.product_id WHERE d.sn=?",r.get("device_sn"));
        List<Map<String,Object>> events = new ArrayList<>();
        for (Map<String,Object> e:jdbc.queryForList("SELECT * FROM huawei_mall_mock.t_repair_progress_event WHERE repair_order_id=? ORDER BY event_time,id",r.get("id"))) {
            events.add(map("time",iso(e.get("event_time")),"status",e.get("status"),"description",e.get("description")));
        }
        String phone = String.valueOf(r.get("contact_phone"));
        return map("serviceNo",r.get("service_no"),"productName",p.get("product_name"),"repairStatus",r.get("status"),
                "currentStep",r.get("current_step"),"progressEvents",events,
                "estimatedCompleteTime",iso(r.get("estimated_complete_time")),"completedAt",iso(r.get("completed_at")),
                "contactMasked",phone.substring(0,3)+"****"+phone.substring(7));
    }

    private Map<String,Object> first(String sql, Object... parameters) {
        List<Map<String,Object>> rows = jdbc.queryForList(sql,parameters);
        return rows.isEmpty()?null:rows.get(0);
    }

    private HuaweiMallClock.Range validRange(String value) {
        try { return HuaweiMallClock.lastMonth(value); }
        catch (IllegalArgumentException e) { throw invalid("INVALID_TIME_RANGE","timeRange","Only LAST_MONTH is supported"); }
    }

    private String required(Map<String,Object> args, String field, int max) {
        String value = optional(args,field,max,null);
        if (value == null) throw invalid("REQUIRED_PARAMETER_MISSING",field,"Required parameter is missing");
        return value;
    }

    private String optional(Map<String,Object> args, String field, int max, String fallback) {
        Object value = args.get(field);
        if (value == null) return fallback;
        if (!(value instanceof String s) || s.isBlank() || s.length()>max) throw invalid("INVALID_PARAMETER",field,"Expected a non-empty string within length limit");
        return s;
    }

    private void oneOf(String value, Set<String> allowed, String field) {
        if (!allowed.contains(value)) throw invalid("INVALID_ENUM",field,"Unsupported enum value");
    }

    private BigDecimal decimal(Map<String,Object> args, String field) {
        Object value = args.get(field);
        if (value == null) return null;
        if (!(value instanceof Number number)) throw invalid("INVALID_PARAMETER",field,"Expected a number");
        try { return new BigDecimal(number.toString()); }
        catch (NumberFormatException e) { throw invalid("INVALID_PARAMETER",field,"Expected a finite number"); }
    }

    private List<String> stringArray(Map<String,Object> args, String field, Set<String> allowed) {
        Object value = args.get(field);
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.isEmpty()) throw invalid("INVALID_ARRAY",field,"Expected a non-empty string array");
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (Object item:list) {
            if (!(item instanceof String s) || s.isBlank() || s.length()>64) throw invalid("INVALID_ARRAY",field,"Array elements must be non-empty strings");
            if (allowed != null && !allowed.contains(s)) throw invalid("INVALID_ENUM",field,"Unsupported enum value");
            deduplicated.add(s);
        }
        if (deduplicated.size()>20) throw invalid("INVALID_ARRAY",field,"At most 20 distinct values are allowed");
        return List.copyOf(deduplicated);
    }

    private Map<String,Object> spec(Object value) {
        try { return mapper.readValue(String.valueOf(value),new TypeReference<>(){}); }
        catch (Exception e) { throw new IllegalStateException("Product specs invalid",e); }
    }

    private List<String> specList(Map<String,Object> spec, String field) {
        Object value = spec.get(field);
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private static BigDecimal money(Object value) { return new BigDecimal(String.valueOf(value)).setScale(2); }
    private static Instant instant(Object value) {
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof java.util.Date d) return d.toInstant();
        if (value instanceof java.time.OffsetDateTime d) return d.toInstant();
        throw new IllegalStateException("Timestamp expected");
    }
    private static String iso(Object value) { return value == null?null:HuaweiMallClock.iso(instant(value)); }
    private static LocalDate date(Object value) {
        if (value == null) return null;
        if (value instanceof Date d) return d.toLocalDate();
        if (value instanceof LocalDate d) return d;
        throw new IllegalStateException("Date expected");
    }
    private static String reasonText(String reason) {
        return switch(reason) {
            case "ELIGIBLE" -> "Coupon is eligible";
            case "EXPIRED" -> "Coupon is expired";
            case "PRODUCT_NOT_SUPPORTED" -> "Product category is not supported";
            case "MIN_AMOUNT_NOT_MET" -> "Minimum amount is not met";
            case "USER_NOT_ELIGIBLE" -> "Principal does not own this coupon";
            case "COUPON_ALREADY_USED" -> "Coupon has already been used";
            default -> "Coupon is not eligible";
        };
    }

    private Result ok(Map<String,Object> data) { return result("OK",data,List.of(),null); }
    private Result notFound() { return result("NOT_FOUND",Map.of(),List.of(),null); }
    private Result conflict(String code,String field,String message) {
        return result("CONFLICT",Map.of(),List.of(),Map.of("code",code,"message",message,
                "fieldErrors",List.of(Map.of("field",field,"message",message))));
    }
    private ToolProblem invalid(String code,String field,String message) { return new ToolProblem("VALIDATION_ERROR",code,field,message); }
    private Result result(String status,Map<String,Object> data,List<Map<String,Object>> candidates,Map<String,Object> error) {
        return new Result(status,data,candidates,error,
                Map.of("asOf",HuaweiMallClock.iso(HuaweiMallClock.AS_OF.toInstant()),"timezone","Asia/Shanghai","datasetVersion",datasetVersion));
    }

    public record Result(String status,Map<String,Object> data,List<Map<String,Object>> candidates,
                         Map<String,Object> error,Map<String,Object> meta) {
        public boolean mcpIsError() { return Set.of("VALIDATION_ERROR","CONFLICT","INTERNAL_ERROR").contains(status); }
    }
    private static final class ToolProblem extends RuntimeException {
        private final String status,code,field;
        ToolProblem(String status,String code,String field,String message) {
            super(message); this.status=status; this.code=code; this.field=field;
        }
    }
    private static Map<String,Object> map(Object... fields) {
        Map<String,Object> values = new LinkedHashMap<>();
        for (int i=0;i<fields.length;i+=2) values.put((String)fields[i],fields[i+1]);
        return values;
    }
}
