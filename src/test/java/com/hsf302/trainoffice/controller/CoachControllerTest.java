package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.service.TrainService;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoachControllerTest {

    private final CoachService coachService = mock(CoachService.class);
    private final TrainService trainService = mock(TrainService.class);
    private final CoachController controller = new CoachController(coachService, trainService);

    @Test
    void addAndEditFormsReceiveDatabaseTypesAndEditSelectsCurrentType() {
        when(coachService.getAvailableCoachTypes()).thenReturn(List.of("Sleeper", "Soft Seat", "Premium Cabin"));
        when(trainService.getAllTrains()).thenReturn(List.of());
        Coach coach = coach("Premium Cabin");
        coach.setCoachId(4L);
        when(coachService.getCoachById(4L)).thenReturn(Optional.of(coach));
        when(trainService.getTrainById(1L)).thenReturn(Optional.of(coach.getTrain()));

        ExtendedModelMap createModel = new ExtendedModelMap();
        assertEquals("coaches/form", controller.showCreateForm(createModel, null));
        assertEquals(List.of("Sleeper", "Soft Seat", "Premium Cabin"), createModel.get("availableCoachTypes"));

        ExtendedModelMap editModel = new ExtendedModelMap();
        assertEquals("coaches/form", controller.showEditForm(4L, editModel));
        assertEquals("Premium Cabin", editModel.get("selectedCoachType"));
    }

    @Test
    void otherSelectionSavesTrimmedNewType() {
        Coach coach = coach(null);
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(coach, "coach");

        assertEquals("redirect:/admin/coaches?trainId=1", controller.saveCoach(
                coach, result, "__OTHER__", "  Premium Cabin  ",
                new ExtendedModelMap(), new RedirectAttributesModelMap()));
        assertEquals("Premium Cabin", coach.getCoachType());
        verify(coachService).saveCoach(coach);
    }

    @Test
    void blankOtherTypeReturnsFormWithValidationError() {
        when(coachService.getAvailableCoachTypes()).thenReturn(List.of("Soft Seat"));
        when(trainService.getTrainById(1L)).thenReturn(Optional.of(new Train()));
        Coach coach = coach(null);
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(coach, "coach");
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("coaches/form", controller.saveCoach(
                coach, result, "__OTHER__", "   ", model, new RedirectAttributesModelMap()));
        assertTrue(result.hasFieldErrors("coachType"));
        assertEquals("__OTHER__", model.get("selectedCoachType"));
        verify(coachService, never()).saveCoach(any());
    }

    @Test
    void blockedDeleteRedirectsWithFriendlyErrorFlash() {
        doThrow(new ResourceInUseException(
                "Cannot delete this coach because it still contains 40 seats."))
                .when(coachService).deleteCoach(8L);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals("redirect:/admin/coaches?trainId=1", controller.deleteCoach(8L, 1L, redirect));
        assertEquals("Cannot delete this coach because it still contains 40 seats.",
                redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void successfulDeleteRedirectsWithSuccessFlash() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertEquals("redirect:/admin/coaches", controller.deleteCoach(9L, null, redirect));
        assertEquals("Coach with ID 9 has been deleted.",
                redirect.getFlashAttributes().get("successMessage"));
        verify(coachService).deleteCoach(9L);
    }

    private Coach coach(String type) {
        Train train = new Train();
        train.setTrainId(1L);
        Coach coach = new Coach();
        coach.setTrain(train);
        coach.setCoachType(type);
        coach.setCoachNumber("C01");
        coach.setCapacity(40);
        return coach;
    }
}
