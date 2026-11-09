package com.hsf302.trainoffice.redis;

import com.hsf302.trainoffice.dto.RateLimitResult;
import com.hsf302.trainoffice.ratelimit.RateLimitInterceptor;
import com.hsf302.trainoffice.ratelimit.RateLimitRule;
import com.hsf302.trainoffice.service.impl.RedisRateLimitService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Chạy với Redis thật (xem RedisTestSupport). */
class RedisRateLimitTest {

    private static RedisTestSupport support;
    private static RedisRateLimitService rateLimits;

    @BeforeAll
    static void connect() {
        support = new RedisTestSupport();
        rateLimits = new RedisRateLimitService(support.redis, support.keyPrefix);
    }

    @AfterEach
    void cleanup() {
        support.cleanup();
    }

    @AfterAll
    static void disconnect() {
        support.close();
    }

    @Test
    void allowsUpToLimitThenBlocksUntilWindowEnds() throws InterruptedException {
        Duration window = Duration.ofMillis(800);

        for (int i = 1; i <= 3; i++) {
            RateLimitResult result = rateLimits.tryConsume("test", "ip:1.2.3.4", 3, window);
            assertTrue(result.allowed(), "request " + i + " should pass");
            assertEquals(i, result.count());
        }

        RateLimitResult blocked = rateLimits.tryConsume("test", "ip:1.2.3.4", 3, window);
        assertFalse(blocked.allowed());
        assertTrue(blocked.retryAfterSeconds() >= 1);

        // Người khác không bị ảnh hưởng.
        assertTrue(rateLimits.tryConsume("test", "ip:5.6.7.8", 3, window).allowed());

        Thread.sleep(1_000);
        assertTrue(rateLimits.tryConsume("test", "ip:1.2.3.4", 3, window).allowed(), "new window starts");
    }

    @Test
    void interceptorReturns429JsonForApiAndForwardsHtmlRequests() throws Exception {
        RateLimitRule rule = new RateLimitRule("test-api", List.of("POST"), List.of("/api/booking/holds/seats"),
                RateLimitRule.Subject.IP, 2, Duration.ofMinutes(1));
        RateLimitRule loginRule = new RateLimitRule("test-login", List.of("POST"), List.of("/login"),
                RateLimitRule.Subject.EMAIL, 1, Duration.ofMinutes(1));
        RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimits, List.of(rule, loginRule));

        for (int i = 0; i < 2; i++) {
            assertTrue(interceptor.preHandle(apiRequest(), new MockHttpServletResponse(), new Object()));
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(apiRequest(), response, new Object()));
        assertEquals(429, response.getStatus());
        assertNotNull(response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));

        // GET không nằm trong quy tắc -> không bị đếm.
        MockHttpServletRequest get = apiRequest();
        get.setMethod("GET");
        assertTrue(interceptor.preHandle(get, new MockHttpServletResponse(), new Object()));

        // Trang thường: đếm theo email, vượt giới hạn thì forward sang /rate-limited.
        assertTrue(interceptor.preHandle(loginRequest("A@Example.com"), new MockHttpServletResponse(), new Object()));
        MockHttpServletResponse htmlResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(loginRequest("a@example.com"), htmlResponse, new Object()));
        assertEquals(429, htmlResponse.getStatus());
        assertEquals(RateLimitInterceptor.BLOCKED_PATH, htmlResponse.getForwardedUrl());

        // Email khác vẫn đăng nhập được.
        assertTrue(interceptor.preHandle(loginRequest("b@example.com"), new MockHttpServletResponse(), new Object()));
    }

    private static MockHttpServletRequest apiRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/booking/holds/seats");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("Accept", "application/json");
        return request;
    }

    private static MockHttpServletRequest loginRequest(String email) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("Accept", "text/html");
        request.addParameter("email", email);
        return request;
    }
}
