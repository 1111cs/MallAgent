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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Fixed business clock and the only relative-time normalization allowed in V1.1. */
public final class HuaweiMallClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final ZonedDateTime AS_OF = ZonedDateTime.parse("2026-09-14T10:00:00+08:00").withZoneSameInstant(ZONE);
    public static final Clock FIXED = Clock.fixed(AS_OF.toInstant(), ZONE);
    private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private HuaweiMallClock() { }

    public static Range lastMonth(String value) {
        if (!"LAST_MONTH".equals(value)) {
            throw new IllegalArgumentException("timeRange supports LAST_MONTH only");
        }
        LocalDate first = LocalDate.now(FIXED).withDayOfMonth(1).minusMonths(1);
        return new Range(first.atStartOfDay(ZONE).toInstant(), first.plusMonths(1).atStartOfDay(ZONE).toInstant());
    }

    public static String iso(Instant instant) {
        return instant == null ? null : ISO_SECONDS.format(OffsetDateTime.ofInstant(instant, ZONE));
    }

    public record Range(Instant startInclusive, Instant endExclusive) { }
}
