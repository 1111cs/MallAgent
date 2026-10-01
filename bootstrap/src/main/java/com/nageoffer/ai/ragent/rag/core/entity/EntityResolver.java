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

package com.nageoffer.ai.ragent.rag.core.entity;

import com.nageoffer.ai.ragent.rag.core.mcp.HuaweiMallRuntimeArguments;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EntityResolver {
    private final EntityCandidateProvider provider;
    private final HuaweiMallRuntimeArguments runtimeArguments;

    public Optional<String> targetFor(String toolId) {
        return switch (toolId) {
            case "getSparePartPrice" -> Optional.of("productId");
            case "getWarrantyStatus" -> Optional.of("sn");
            case "getCouponEligibility" -> Optional.of("couponId");
            default -> Optional.empty();
        };
    }

    public EntityResolutionResult resolve(EntityResolutionRequest request) {
        String principal = runtimeArguments.resolvePrincipal(request.authenticatedUserId());
        if (principal == null) return EntityResolutionResult.notFound(type(request));
        List<EntityCandidate> domain = domain(request, principal);
        List<EntityCandidate> matches = match(request, domain);
        if (matches.size() == 1) return EntityResolutionResult.unique(matches.get(0), matches, type(request));
        if (matches.size() > 1) return EntityResolutionResult.ambiguous(matches, type(request));
        return EntityResolutionResult.notFound(type(request));
    }

    public Optional<EntityCandidate> revalidate(EntityResolutionRequest request, String candidateId) {
        String principal = runtimeArguments.resolvePrincipal(request.authenticatedUserId());
        if (principal == null) return Optional.empty();
        return domain(request, principal).stream()
                .filter(candidate -> candidate.candidateId().equals(candidateId))
                .filter(candidate -> candidate.bindings().containsKey(request.parameter()))
                .findFirst();
    }

    private List<EntityCandidate> domain(EntityResolutionRequest request, String principal) {
        return switch (request.toolId() + ":" + request.parameter()) {
            case "getWarrantyStatus:sn" -> provider.findOwnedDevices(principal);
            case "getCouponEligibility:couponId" -> provider.findOwnedCoupons(principal);
            case "getSparePartPrice:productId" -> hasSpecificProductName(request.expression())
                    ? provider.findProducts()
                    : category(normalize(request.expression())) != null
                            ? provider.findOwnedDevices(principal) : provider.findProducts();
            default -> List.of();
        };
    }

    private List<EntityCandidate> match(EntityResolutionRequest request, List<EntityCandidate> candidates) {
        String expression = normalize(request.expression());
        if (expression.isBlank()) return List.of();
        List<EntityCandidate> exact = candidates.stream()
                .filter(candidate -> bindingAppears(candidate, request.parameter(), expression))
                .toList();
        if (!exact.isEmpty()) return exact;

        if ("getCouponEligibility".equals(request.toolId())) {
            return expression.contains("我的优惠券") ? candidates : List.of();
        }
        String category = category(expression);
        if ("getWarrantyStatus".equals(request.toolId())) {
            if (category == null) return List.of();
            return candidates.stream().filter(candidate -> category.equals(candidate.bindings().get("category"))).toList();
        }
        if ("getSparePartPrice".equals(request.toolId())) {
            List<EntityCandidate> names = candidates.stream().filter(candidate -> nameAppears(candidate, expression)).toList();
            if (!names.isEmpty()) {
                int mostSpecific = names.stream().mapToInt(this::nameSpecificity).max().orElse(0);
                return names.stream().filter(candidate -> nameSpecificity(candidate) == mostSpecific).toList();
            }
            if (ownedCandidateDomain(candidates) && category != null) {
                return candidates.stream().filter(candidate -> category.equals(candidate.bindings().get("category"))).toList();
            }
        }
        return List.of();
    }

    private boolean bindingAppears(EntityCandidate candidate, String target, String expression) {
        Object value = candidate.bindings().get(target);
        return value != null && expression.contains(normalize(value.toString()));
    }

    private boolean nameAppears(EntityCandidate candidate, String expression) {
        String model = normalizedModel(candidate);
        return model.length() >= 4 && expression.contains(model);
    }

    private int nameSpecificity(EntityCandidate candidate) {
        return normalizedModel(candidate).length();
    }

    private String normalizedModel(EntityCandidate candidate) {
        return normalize(String.valueOf(candidate.bindings().get("model"))).replaceAll("20\\d{2}", "");
    }

    private String category(String expression) {
        if (expression.contains("手表") || expression.contains("watch")) return "wearable";
        if (expression.contains("笔记本") || expression.contains("matebook")) return "laptop";
        if (expression.contains("手机") || expression.contains("mate") || expression.contains("pura")) return "phone";
        if (expression.contains("平板") || expression.contains("matepad")) return "tablet";
        return null;
    }

    private boolean hasSpecificProductName(String expression) {
        String value = normalize(expression);
        return value.contains("matebook") || value.matches(".*p[a-z0-9]*[0-9].*");
    }

    private boolean ownedCandidateDomain(List<EntityCandidate> candidates) {
        return candidates.stream().anyMatch(candidate -> candidate.bindings().containsKey("sn"));
    }

    private boolean isOwnedExpression(String expression) {
        String value = normalize(expression);
        return value.contains("我的") || value.contains("我那个") || value.contains("我上个月买的")
                || value.contains("上个月买的");
    }

    private String type(EntityResolutionRequest request) {
        return switch (request.parameter()) {
            case "sn" -> "DEVICE";
            case "couponId" -> "COUPON";
            case "productId" -> "PRODUCT";
            default -> "UNKNOWN";
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_，。！？,.!?()（）-]", "");
    }
}
