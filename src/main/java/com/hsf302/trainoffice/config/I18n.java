package com.hsf302.trainoffice.config;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Lấy chuỗi đa ngôn ngữ trong code Java (controller, util...) theo ngôn ngữ của request hiện tại.
 *
 * <pre>
 *   redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.profile.updated"));
 *   I18n.t("msg.coach.deleted", id)   // "Đã xóa toa có ID {0}."
 * </pre>
 *
 * Key được khai báo trong messages.properties (VI) và messages_en.properties (EN).
 */
@Component
public class I18n {

    private static MessageSource messageSource;

    public I18n(MessageSource messageSource) {
        I18n.messageSource = messageSource;
    }

    public static String t(String key, Object... args) {
        if (messageSource == null) {
            return key;
        }
        Object[] safeArgs = args == null || args.length == 0 ? null : toDisplayArgs(args);
        try {
            return messageSource.getMessage(key, safeArgs, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException ex) {
            return key;
        }
    }

    /** Đổi số thành chuỗi để MessageFormat không chèn dấu phân cách hàng nghìn vào ID (1,234). */
    private static Object[] toDisplayArgs(Object[] args) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            result[i] = arg instanceof Number ? String.valueOf(arg) : (arg == null ? "" : arg);
        }
        return result;
    }
}
