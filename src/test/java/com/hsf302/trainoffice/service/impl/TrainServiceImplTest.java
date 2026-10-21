package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.repository.TrainRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainServiceImplTest {

    private final TrainRepository repository = mock(TrainRepository.class);
    private final TrainServiceImpl service = new TrainServiceImpl(repository);

    @Test
    void availableTypesAreTrimmedFilteredDeduplicatedAndSorted() {
        when(repository.findDistinctTrainTypes()).thenReturn(Arrays.asList(
                " Night Train ", "Express", null, "", "Local", "express", "   "));

        assertEquals(List.of("Express", "Local", "Night Train"), service.getAvailableTrainTypes());
    }

    @Test
    void selectingExistingTypeSavesNormally() {
        when(repository.findDistinctTrainTypes()).thenReturn(List.of("Express", "Local"));
        when(repository.save(any(Train.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Train train = train("SE10", "Express");

        Train saved = service.saveTrain(train);

        assertEquals("Express", saved.getTrainType());
        verify(repository).save(train);
    }

    @Test
    void newTypeIsSavedAndAppearsInNextAvailableList() {
        when(repository.findDistinctTrainTypes())
                .thenReturn(List.of("Express", "Local"))
                .thenReturn(List.of("Express", "Local", "Luxury Express"));
        when(repository.save(any(Train.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Train train = train("LX01", "  Luxury Express  ");

        assertEquals("Luxury Express", service.saveTrain(train).getTrainType());
        assertEquals(List.of("Express", "Local", "Luxury Express"), service.getAvailableTrainTypes());
    }

    @Test
    void caseInsensitiveExistingTypeUsesStoredCanonicalValue() {
        when(repository.findDistinctTrainTypes()).thenReturn(List.of("Express", "Local"));
        when(repository.save(any(Train.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Train train = train("EX02", " EXPRESS ");

        assertEquals("Express", service.saveTrain(train).getTrainType());
    }

    @Test
    void blankNewTypeCannotBeSaved() {
        Train train = train("BAD1", "   ");

        assertEquals("Train type is required.",
                assertThrows(IllegalStateException.class, () -> service.saveTrain(train)).getMessage());
        verify(repository, never()).save(any());
    }

    private Train train(String code, String type) {
        Train train = new Train();
        train.setTrainCode(code);
        train.setTrainName("Test Train");
        train.setTrainType(type);
        return train;
    }
}
