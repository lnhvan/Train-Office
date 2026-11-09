package com.hsf302.trainoffice.ratelimit;

import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.RateLimitResult;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.security.CurrentUser;
import com.hsf302.trainoffice.service.RateLimitService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Chặn request vượt giới hạn trong {@link RateLimitRules}: trả 429 kèm header Retry-After.
 * API JSON nhận body JSON; trang thường được chuyển sang trang thông báo /rate-limited.
 * Redis lỗi thì cho request đi qua (rate limit chỉ là lớp bảo vệ phụ).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String RETRY_AFTER_ATTRIBUTE = "rateLimitRetryAfterSeconds";
    public static final String BLOCKED_PATH = "/rate-limited";

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<RateLimitRule> rules;

    @Autowired
    public RateLimitInterceptor(RateLimitService rateLimitService) {
        this(rateLimitService, RateLimitRules.RULES);
    }

    public RateLimitInterceptor(RateLimitService rateLimitService, List<RateLimitRule> rules) {
        this.rateLimitService = rateLimitService;
        this.rules = rules;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // Chỉ đếm request gốc, không đếm lần forward sang trang lỗi.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        for (RateLimitRule rule : rules) {
            if (!rule.methods().contains(method) || rule.patterns().stream().noneMatch(p -> pathMatcher.match(p, path))) {
                continue;
            }

            String subject = subject(rule, request);
            if (subject == null) {
                continue;
            }

            RateLimitResult result;
            try {
                result = rateLimitService.tryConsume(rule.name(), subject, rule.limit(), rule.window());
            } catch (DataAccessException ex) {
                log.warn("Rate limit skipped, Redis unavailable: {}", ex.getMessage());
                return true;
            }

            if (!result.allowed()) {
                log.warn("[RateLimit] BLOCKED {} {} rule={} subject={} count={}/{} retryAfter={}s",
                        method, path, rule.name(), subject, result.count(), rule.limit(), result.retryAfterSeconds());
                reject(request, response, path, result.retryAfterSeconds());
                return false;
            }

            log.info("[RateLimit] {} {} rule={} subject={} count={}/{} window={}s",
                    method, path, rule.name(), subject, result.count(), rule.limit(), rule.window().toSeconds());
        }

        return true;
    }

    private String subject(RateLimitRule rule, HttpServletRequest request) {
        return switch (rule.subject()) {
            case IP -> "ip:" + clientIp(request);
            case USER -> {
                User user = CurrentUser.get(request);
                yield user != null && user.getUserId() != null
                        ? "user:" + user.getUserId()
                        : "ip:" + clientIp(request);
            }
            case EMAIL -> {
                String email = request.getParameter("email");
                yield email == null || email.isBlank() ? null : "email:" + email.trim().toLowerCase(Locale.ROOT);
            }
        };
    }

    /** IP của client. Chạy sau reverse proxy thì bật server.forward-headers-strategy=native. */
    private static String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    private void reject(HttpServletRequest request,
                        HttpServletResponse response,
                        String path,
                        long retryAfterSeconds) throws Exception {
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setStatus(429);

        String message = I18n.t("err.tooManyRequests", retryAfterSeconds);
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        boolean wantsJson = path.startsWith("/api/")
                || (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE) && !accept.contains("text/html"));

        if (wantsJson) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"retryAfter\":" + retryAfterSeconds
                    + ",\"error\":\"" + jsonEscape(message) + "\"}");
            return;
        }

        request.setAttribute(RETRY_AFTER_ATTRIBUTE, retryAfterSeconds);
        request.getRequestDispatcher(BLOCKED_PATH).forward(request, response);
    }

    private static String jsonEscape(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
