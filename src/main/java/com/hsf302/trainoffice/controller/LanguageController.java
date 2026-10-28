package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.config.I18nConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.LocaleResolver;

import java.net.URI;
import java.util.Locale;

/**
 * Đổi ngôn ngữ giao diện rồi quay lại đúng trang người dùng đang xem.
 * Ví dụ: GET /language/en  hoặc  GET /language/vi
 */
@Controller
public class LanguageController {

    private final LocaleResolver localeResolver;

    public LanguageController(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver;
    }

    @GetMapping("/language/{lang}")
    public String changeLanguage(@PathVariable String lang,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        Locale locale = I18nConfig.toSupportedLocale(lang);
        if (locale != null) {
            localeResolver.setLocale(request, response, locale);
        }
        return "redirect:" + safeReturnPath(request);
    }

    /**
     * Lấy đường dẫn trang trước (Referer) nhưng chỉ giữ phần path + query
     * của cùng ứng dụng, tránh redirect ra website khác.
     */
    private String safeReturnPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String fallback = contextPath.isEmpty() ? "/" : contextPath + "/";
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return fallback;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getHost() != null && !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                return fallback;
            }
            String path = uri.getRawPath();
            if (path == null || path.isEmpty() || !path.startsWith("/") || path.startsWith("//")
                    || path.startsWith(contextPath + "/language/")) {
                return fallback;
            }
            String query = stripLangParam(uri.getRawQuery());
            // redirect: trong Spring tự thêm context path, nên bỏ context path ở đây
            String relative = contextPath.isEmpty() ? path : path.substring(Math.min(contextPath.length(), path.length()));
            if (relative.isEmpty()) {
                relative = "/";
            }
            return query == null ? relative : relative + "?" + query;
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private String stripLangParam(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        StringBuilder kept = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.isEmpty() || part.equals(I18nConfig.LANG_PARAM) || part.startsWith(I18nConfig.LANG_PARAM + "=")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(part);
        }
        return kept.length() == 0 ? null : kept.toString();
    }
}
