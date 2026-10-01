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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/** Server-side verification of the principal supplied by the authenticated Bootstrap. */
public final class HuaweiMallRuntimeProof {
    private final byte[] secret;

    public HuaweiMallRuntimeProof(String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    public String requirePrincipal(String toolName, Map<String, Object> arguments) {
        if (secret.length < 32) {
            throw new IllegalStateException("Huawei Mall runtime secret is not configured");
        }
        Object principal = arguments.get("_runtimePrincipal");
        Object issuedAt = arguments.get("_runtimeIssuedAt");
        Object proof = arguments.get("_runtimeProof");
        if (!(principal instanceof String p) || !p.matches("U[0-9]{3}")
                || !(issuedAt instanceof Number n) || !(proof instanceof String signature)) {
            throw new IllegalArgumentException("Trusted runtime identity is missing");
        }
        long seconds = n.longValue();
        if (Math.abs(Instant.now().getEpochSecond() - seconds) > 60) {
            throw new IllegalArgumentException("Trusted runtime identity expired");
        }
        byte[] expected = hmac(toolName + "\n" + p + "\n" + seconds);
        byte[] received;
        try {
            received = HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trusted runtime identity is invalid");
        }
        if (!MessageDigest.isEqual(expected, received)) {
            throw new IllegalArgumentException("Trusted runtime identity is invalid");
        }
        return p;
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
