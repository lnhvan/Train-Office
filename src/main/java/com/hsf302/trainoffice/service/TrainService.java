package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Train;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface TrainService {
    /** Giới hạn hợp lệ cho tốc độ trung bình của tàu (km/h). */
    int MIN_SPEED_KMH = 20;
    int MAX_SPEED_KMH = 300;

    static boolean isValidSpeed(Integer speedKmh) {
        return speedKmh != null && speedKmh >= MIN_SPEED_KMH && speedKmh <= MAX_SPEED_KMH;
    }

    List<Train> getAllTrains();

    Page<Train> listAll(int pageNumber, String keyword);

    Optional<Train> getTrainById(Long id);

    Train saveTrain(Train train);

    List<String> getAvailableTrainTypes();

    void deleteTrain(Long id);
}
