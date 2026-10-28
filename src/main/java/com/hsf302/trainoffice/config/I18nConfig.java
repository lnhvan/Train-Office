package com.hsf302.trainoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Cấu hình đa ngôn ngữ (Tiếng Việt / English).
 *
 * - Ngôn ngữ mặc định: Tiếng Việt.
 * - Ngôn ngữ người dùng chọn được lưu trong cookie "RAILJET_LANG" (1 năm).
 * - Đổi ngôn ngữ bằng tham số ?lang=vi hoặc ?lang=en trên bất kỳ URL nào,
 *   hoặc qua endpoint /language/{lang} (xem LanguageController).
 * - Chuỗi giao diện nằm trong src/main/resources/messages*.properties.
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    public static final Locale VIETNAMESE = Locale.forLanguageTag("vi");
    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final List<Locale> SUPPORTED_LOCALES = List.of(VIETNAMESE, ENGLISH);
    public static final String LANG_PARAM = "lang";
    public static final String LANG_COOKIE = "RAILJET_LANG";

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver(LANG_COOKIE);
        resolver.setDefaultLocale(VIETNAMESE);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(LANG_PARAM);
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor()).addPathPatterns("/**");
    }

    /** Chỉ chấp nhận ngôn ngữ được hỗ trợ, ngược lại trả về null. */
    public static Locale toSupportedLocale(String lang) {
        if (lang == null) {
            return null;
        }
        String normalized = lang.trim().toLowerCase(Locale.ROOT);
        for (Locale locale : SUPPORTED_LOCALES) {
            if (locale.getLanguage().equals(normalized)) {
                return locale;
            }
        }
        return null;
    }
}
