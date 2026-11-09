package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.service.TrainTripService;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.TrainService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainControllerTest {

    private final TrainService trainService = mock(TrainService.class);
    private final TrainController controller = new TrainController(trainService);

    @Test
    void addAndEditFormsReceiveDatabaseTypesAndEditSelectsCurrentType() {
        when(trainService.getAvailableTrainTypes()).thenReturn(List.of("Express", "Local", "Luxury Express"));
        Train train = new Train();
        train.setTrainId(4L);
        train.setTrainType("Luxury Express");
        when(trainService.getTrainById(4L)).thenReturn(Optional.of(train));

        ExtendedModelMap createModel = new ExtendedModelMap();
        assertEquals("trains/form", controller.showCreateForm(createModel));
        assertEquals(List.of("Express", "Local", "Luxury Express"), createModel.get("availableTrainTypes"));

        ExtendedModelMap editModel = new ExtendedModelMap();
        assertEquals("trains/form", controller.showEditForm(
                4L, editModel, new RedirectAttributesModelMap()));
        assertEquals("Luxury Express", editModel.get("selectedTrainType"));
    }

    @Test
    void otherSelectionSavesTrimmedNewType() {
        when(trainService.getAvailableTrainTypes()).thenReturn(List.of("Express"));
        when(trainService.saveTrain(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Train train = new Train();
        train.setTrainCode("LX01");
        train.setTrainName("Luxury");
        train.setAverageSpeedKmh(60);
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(train, "train");

        assertEquals("redirect:/admin/trains", controller.saveTrain(
                train, result, "__OTHER__", "  Luxury Express  ",
                new ExtendedModelMap(), new RedirectAttributesModelMap()));
        assertEquals("Luxury Express", train.getTrainType());
        verify(trainService).saveTrain(train);
    }

    @Test
    void blankOtherTypeReturnsFormWithValidationError() {
        when(trainService.getAvailableTrainTypes()).thenReturn(List.of("Express"));
        Train train = new Train();
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(train, "train");
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("trains/form", controller.saveTrain(
                train, result, "__OTHER__", "   ", model, new RedirectAttributesModelMap()));
        assertTrue(result.hasFieldErrors("trainType"));
        assertEquals("__OTHER__", model.get("selectedTrainType"));
        verify(trainService, never()).saveTrain(any());
    }
    @Test
    void transitioningToMaintenanceCancelsScheduledTripsAndDisplaysMessage() {
        TrainTripService tripService = mock(TrainTripService.class);
        TrainTripRepository tripRepo = mock(TrainTripRepository.class);
        TrainController ctrl = new TrainController(trainService, tripService, tripRepo);

        when(trainService.getAvailableTrainTypes()).thenReturn(List.of("Express"));
        Train oldTrain = new Train();
        oldTrain.setTrainId(10L);
        oldTrain.setTrainCode("CT01");
        oldTrain.setStatus(TrainStatus.AVAILABLE);
        when(trainService.getTrainById(10L)).thenReturn(Optional.of(oldTrain));

        Train updatedTrain = new Train();
        updatedTrain.setTrainId(10L);
        updatedTrain.setTrainCode("CT01");
        updatedTrain.setTrainName("Can Tho");
        updatedTrain.setAverageSpeedKmh(60);
        updatedTrain.setStatus(TrainStatus.MAINTENANCE);

        TrainTrip scheduledTrip = new TrainTrip();
        scheduledTrip.setTripId(101L);
        when(tripRepo.findByTrain_TrainIdAndStatusOrderByDepartureTimeAsc(10L, TripStatus.SCHEDULED))
                .thenReturn(List.of(scheduledTrip));
        when(tripService.cancelTrip(101L))
                .thenReturn(new TripCancellationResult(2, java.math.BigDecimal.valueOf(500000), 1));

        BeanPropertyBindingResult result = new BeanPropertyBindingResult(updatedTrain, "train");
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = ctrl.saveTrain(updatedTrain, result, "Express", "", new ExtendedModelMap(), redirect);
        assertEquals("redirect:/admin/trains", view);
        verify(tripService).cancelTrip(101L);
        verify(trainService).saveTrain(updatedTrain);
        assertTrue(redirect.getFlashAttributes().containsKey("successMessage"));
        String msg = (String) redirect.getFlashAttributes().get("successMessage");
        assertTrue(msg.contains("CT01"));
        assertTrue(msg.contains("Chuy\u1ebfn #101") || msg.contains("Chuyến #101"));
        assertTrue(msg.contains("\u0110ang b\u1ea3o tr\u00ec") || msg.contains("Đang bảo trì"));
    }
}