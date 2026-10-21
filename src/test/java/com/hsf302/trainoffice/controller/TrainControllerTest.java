package com.hsf302.trainoffice.controller;

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
}
