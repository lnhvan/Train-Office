package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Coach;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

import java.util.List;
import java.util.Optional;

public interface CoachService {
    List<Coach> getAllCoaches();

    Page<Coach> listAll(int pageNumber, String keyword, Long trainId);

    Optional<Coach> getCoachById(Long id);

    Coach saveCoach(Coach coach);

    List<String> getAvailableCoachTypes();

    void deleteCoach(Long id);

    /**
     * Phụ thu mỗi ghế/giường đang áp cho toa: giá đã lưu trên toa, nếu chưa có thì lấy
     * theo ghế hiện có, cuối cùng là mức mặc định theo loại toa.
     */
    BigDecimal getEffectiveSeatPrice(Coach coach);
}
