package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.dto.RateLimitResult;

import java.time.Duration;

/** Giới hạn số request theo cửa sổ thời gian cố định, đếm trong Redis. */
public interface RateLimitService {

    /**
     * Đếm thêm một request của {@code subject} cho quy tắc {@code rule}.
     *
     * @param limit  số request tối đa trong một cửa sổ
     * @param window độ dài cửa sổ
     */
    RateLimitResult tryConsume(String rule, String subject, int limit, Duration window);
}
