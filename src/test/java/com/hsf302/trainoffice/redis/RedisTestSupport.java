package com.hsf302.trainoffice.redis;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kết nối Redis THẬT cho test (không mock). Mặc định localhost:6379, đổi bằng biến môi trường
 * REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_TEST_DATABASE (mặc định database 15 để không
 * đụng dữ liệu của ứng dụng ở database 0).
 *
 * Chạy Redis: docker run -d --name redis -p 6379:6379 redis:7
 */
final class RedisTestSupport implements AutoCloseable {

    final LettuceConnectionFactory factory;
    final StringRedisTemplate redis;
    /** Tiền tố key riêng cho mỗi lớp test, xóa sạch khi đóng. */
    final String keyPrefix = "rjtest:" + UUID.randomUUID() + ":";

    RedisTestSupport() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                env("REDIS_HOST", "localhost"),
                Integer.parseInt(env("REDIS_PORT", "6379")));
        config.setDatabase(Integer.parseInt(env("REDIS_TEST_DATABASE", "15")));
        String password = env("REDIS_PASSWORD", "");
        if (!password.isBlank()) {
            config.setPassword(password);
        }

        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);

        try {
            redis.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping());
        } catch (RuntimeException ex) {
            close();
            throw new IllegalStateException("Redis is not running at " + config.getHostName() + ":" + config.getPort()
                    + ". Start it with: docker run -d --name redis -p 6379:6379 redis:7", ex);
        }
    }

    void cleanup() {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(keyPrefix + "*").count(500).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Override
    public void close() {
        try {
            cleanup();
        } catch (RuntimeException ignored) {
            // Redis không chạy: không có gì để dọn.
        }
        factory.destroy();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
