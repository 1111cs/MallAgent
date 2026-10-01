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

import com.nageoffer.ai.ragent.framework.context.LoginUser;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HuaweiMallRuntimeArgumentsTest {
    private static final String SECRET="0123456789abcdef0123456789abcdef";
    private final HuaweiMallRuntimeArguments binder=new HuaweiMallRuntimeArguments("authenticated-login=U003",SECRET);

    @AfterEach void clear() { UserContext.clear(); }

    @Test void authenticatedLoginOverridesAnyModelIdentityAndSignsTheFixturePrincipal() throws Exception {
        UserContext.set(LoginUser.builder().userId("authenticated-login").build());
        Map<String,Object> args=binder.bind("getWarrantyStatus",Map.of("sn","WATCH-SN-003","userId","U999","_runtimePrincipal","U999"));
        assertThat(args).containsEntry("sn","WATCH-SN-003").containsEntry("_runtimePrincipal","U003");
        assertThat(args).doesNotContainKeys("userId","principalId");
        long issued=((Number)args.get("_runtimeIssuedAt")).longValue();
        assertThat(Math.abs(Instant.now().getEpochSecond()-issued)).isLessThanOrEqualTo(60);
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String expected=HexFormat.of().formatHex(mac.doFinal(("getWarrantyStatus\nU003\n"+issued).getBytes(StandardCharsets.UTF_8)));
        assertThat(args.get("_runtimeProof")).isEqualTo(expected);
    }

    @Test void publicToolNeedsNoPrincipalAndUnmappedLoginCannotReadPrivateData() {
        assertThat(binder.bind("searchProducts",Map.of("category","laptop")))
                .containsEntry("category","laptop").doesNotContainKey("_runtimePrincipal");
        UserContext.set(LoginUser.builder().userId("other-login").build());
        assertThatThrownBy(() -> binder.bind("getOrder",Map.of())).isInstanceOf(IllegalStateException.class);
    }
}
