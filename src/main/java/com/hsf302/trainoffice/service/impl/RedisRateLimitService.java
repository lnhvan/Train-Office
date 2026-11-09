package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.RateLimitResult;
import com.hsf302.trainoffice.service.RateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Fixed window counter: key {@code rj:rl:<rule>:<subject>} tăng 1 mỗi request,
 * request đầu tiên của cửa sổ đặt TTL = độ dài cửa sổ. Một script Lua nên INCR và PEXPIRE
 * luôn đi cùng nhau (không có key đếm mãi không hết hạn).
 */
@Service
public class RedisRateLimitService implements RateLimitService {

    /** Trả về {số đếm, PTTL còn lại (ms)}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final RedisScript<List> CONSUME = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    @Autowired
    public RedisRateLimitService(StringRedisTemplate redis) {
        this(redis, "rj:");
    }

    /** Dùng cho test: tiền tố key riêng. */
    public RedisRateLimitService(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public RateLimitResult tryConsume(String rule, String subject, int limit, Duration window) {
        String key = keyPrefix + "rl:" + rule + ":" + subject;

        @SuppressWarnings("unchecked")
        List<Object> result = redis.execute(CONSUME, List.of(key), String.valueOf(window.toMillis()));

        long count = toLong(result, 0);
        long ttlMillis = toLong(result, 1);
        boolean allowed = count <= limit;
        long retryAfter = allowed ? 0 : Math.max(1, (ttlMillis + 999) / 1000);

        return new RateLimitResult(allowed, count, retryAfter);
    }

    private static long toLong(List<Object> values, int index) {
        if (values == null || values.size() <= index || values.get(index) == null) {
            return 0;
        }
        Object value = values.get(index);
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
