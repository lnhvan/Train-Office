package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Coach;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface CoachService {
    List<Coach> getAllCoaches();

    Page<Coach> listAll(int pageNumber, String keyword, Long trainId);

    Optional<Coach> getCoachById(Long id);

    Coach saveCoach(Coach coach);

    List<String> getAvailableCoachTypes();

    void deleteCoach(Long id);
}
