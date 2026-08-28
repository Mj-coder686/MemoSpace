package com.memospace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class FeedCacheService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;

    public FeedCacheService(StringRedisTemplate redis, ObjectMapper json,
                            @Value("${app.cache-enabled}") boolean enabled) {
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
    }

    public List<Map<String, Object>> get(long userId, String scope) {
        if (!enabled) return null;
        try {
            String value = redis.opsForValue().get(key(userId, scope));
            return value == null ? null : json.readValue(value, new TypeReference<>() {});
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(long userId, String scope, List<Map<String, Object>> feed) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(key(userId, scope), json.writeValueAsString(feed), Duration.ofMinutes(2));
        } catch (Exception ignored) {
            // Cache failure must never block reading or publishing memories.
        }
    }

    public void invalidateAll() {
        if (!enabled) return;
        try {
            redis.opsForValue().increment("memospace:feed:version");
        } catch (Exception ignored) {
            // Database remains the source of truth.
        }
    }

    private String key(long userId, String scope) {
        String version = redis.opsForValue().get("memospace:feed:version");
        return "memospace:feed:" + (version == null ? "0" : version) + ":" + scope + ":" + userId;
    }
}
