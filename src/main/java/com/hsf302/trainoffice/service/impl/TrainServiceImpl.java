package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.repository.TrainRepository;
import com.hsf302.trainoffice.service.TrainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class TrainServiceImpl implements TrainService {

    private static final int TRAINS_PER_PAGE = 8;

    private final TrainRepository trainRepository;

    @Autowired
    public TrainServiceImpl(TrainRepository trainRepository) {
        this.trainRepository = trainRepository;
    }

    @Override
    public List<Train> getAllTrains() {
        return trainRepository.findAll(Sort.by("trainCode").ascending());
    }

    @Override
    public Page<Train> listAll(int pageNumber, String keyword) {
        int safePageNumber = Math.max(pageNumber, 1);

        Pageable pageable = PageRequest.of(
                safePageNumber - 1,
                TRAINS_PER_PAGE,
                Sort.by("trainCode").ascending()
        );

        String cleanKeyword = normalizeKeyword(keyword);

        return trainRepository.searchTrains(cleanKeyword, pageable);
    }

    @Override
    public Optional<Train> getTrainById(Long id) {
        return trainRepository.findById(id);
    }

    @Override
    public Train saveTrain(Train train) {
        normalizeTrain(train);

        if (train.getTrainType() == null || train.getTrainType().isBlank()) {
            throw new IllegalStateException("Train type is required.");
        }

        getAvailableTrainTypes().stream()
                .filter(existingType -> existingType.equalsIgnoreCase(train.getTrainType()))
                .findFirst()
                .ifPresent(train::setTrainType);

        if (train.getTrainId() == null) {
            if (trainRepository.existsByTrainCode(train.getTrainCode())) {
                throw new IllegalStateException(
                        "Train code '" + train.getTrainCode() + "' already exists!"
                );
            }
        } else {
            Optional<Train> oldTrain = trainRepository.findById(train.getTrainId());

            if (oldTrain.isPresent()
                    && !oldTrain.get().getTrainCode().equals(train.getTrainCode())
                    && trainRepository.existsByTrainCode(train.getTrainCode())) {
                throw new IllegalStateException(
                        "Train code '" + train.getTrainCode() + "' already exists!"
                );
            }
        }

        return trainRepository.save(train);
    }

    @Override
    public List<String> getAvailableTrainTypes() {
        TreeMap<String, String> canonicalTypes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<String> storedTypes = trainRepository.findDistinctTrainTypes();
        if (storedTypes == null) {
            return List.of();
        }

        storedTypes.stream()
                .filter(type -> type != null && !type.isBlank())
                .map(String::trim)
                .forEach(type -> canonicalTypes.putIfAbsent(type, type));

        return canonicalTypes.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public void deleteTrain(Long id) {
        if (!trainRepository.existsById(id)) {
            throw new RuntimeException("Train not found with ID: " + id);
        }

        trainRepository.deleteById(id);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }

    private void normalizeTrain(Train train) {
        if (train.getTrainCode() != null) {
            train.setTrainCode(train.getTrainCode().trim().toUpperCase());
        }

        if (train.getTrainName() != null) {
            train.setTrainName(train.getTrainName().trim());
        }

        if (train.getTrainType() != null) {
            train.setTrainType(train.getTrainType().trim());
        }
    }
}
