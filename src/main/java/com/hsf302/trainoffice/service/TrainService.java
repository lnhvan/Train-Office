package com.hsf302.trainoffice.service;

import com.hsf302.trainoffice.entity.Train;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface TrainService {
    List<Train> getAllTrains();

    Page<Train> listAll(int pageNumber, String keyword);

    Optional<Train> getTrainById(Long id);

    Train saveTrain(Train train);

    List<String> getAvailableTrainTypes();

    void deleteTrain(Long id);
}
