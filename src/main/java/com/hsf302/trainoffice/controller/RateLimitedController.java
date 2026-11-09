package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.ratelimit.RateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Trang thông báo khi khách gửi quá nhiều request (RateLimitInterceptor forward sang đây). */
@Controller
public class RateLimitedController {

    @RequestMapping(RateLimitInterceptor.BLOCKED_PATH)
    public String rateLimited(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object retryAfter = request.getAttribute(RateLimitInterceptor.RETRY_AFTER_ATTRIBUTE);
        long seconds = retryAfter instanceof Number number ? number.longValue() : 60L;

        response.setStatus(429);
        model.addAttribute("retryAfterSeconds", seconds);
        return "error/rate-limited";
    }
}
