package com.hsf302.trainoffice.dto;

/**
 * Kết quả đếm request trong một cửa sổ thời gian.
 *
 * @param allowed           còn trong giới hạn
 * @param count             số request đã đếm trong cửa sổ hiện tại (kể cả request này)
 * @param retryAfterSeconds còn bao lâu cửa sổ mới bắt đầu (khi bị chặn)
 */
public record RateLimitResult(boolean allowed, long count, long retryAfterSeconds) {

    public static RateLimitResult allowAll() {
        return new RateLimitResult(true, 0, 0);
    }
}
