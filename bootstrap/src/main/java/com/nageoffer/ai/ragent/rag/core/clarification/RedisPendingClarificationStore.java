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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisPendingClarificationStore implements PendingClarificationStore {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final String namespace;

    public RedisPendingClarificationStore(RedissonClient redissonClient, ObjectMapper objectMapper,
                                          @Value("${unique-name:}") String uniqueName) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.namespace = "ragent:pending" + (uniqueName == null ? "" : uniqueName);
    }

    @Override
    public Optional<PendingClarificationState> get(String userId, String conversationId) {
        return deserialize(bucket(userId, conversationId).get());
    }

    @Override
    public void saveWithTtl(PendingClarificationState state) {
        try {
            bucket(state.getAuthenticatedUserId(), state.getConversationId())
                    .set(objectMapper.writeValueAsString(state), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist pending clarification", e);
        }
    }

    @Override
    public Optional<PendingClarificationState> take(String userId, String conversationId) {
        return deserialize(bucket(userId, conversationId).getAndDelete());
    }

    @Override
    public void cancel(String userId, String conversationId) {
        bucket(userId, conversationId).delete();
    }

    private RBucket<String> bucket(String userId, String conversationId) {
        return redissonClient.getBucket(namespace + ":" + userId + ":" + conversationId, StringCodec.INSTANCE);
    }

    private Optional<PendingClarificationState> deserialize(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, PendingClarificationState.class));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read pending clarification", e);
        }
    }
}
