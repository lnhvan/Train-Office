package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.repository.*;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoachServiceImplTest {

    private final CoachRepository coachRepository = mock(CoachRepository.class);
    private final SeatRepository seatRepository = mock(SeatRepository.class);
    private final CompartmentRepository compartmentRepository = mock(CompartmentRepository.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final CoachServiceImpl service = new CoachServiceImpl(
            coachRepository, seatRepository, compartmentRepository, ticketRepository);

    @Test
    void availableTypesAreTrimmedFilteredDeduplicatedAndSorted() {
        when(coachRepository.findDistinctCoachTypes()).thenReturn(Arrays.asList(
                " Sleeper ", "Soft Seat", null, "", "Hard Seat", "SOFT SEAT", "   "));

        assertEquals(List.of("Hard Seat", "Sleeper", "Soft Seat"), service.getAvailableCoachTypes());
    }

    @Test
    void selectingExistingTypeSavesNormally() {
        when(coachRepository.findDistinctCoachTypes()).thenReturn(List.of("Sleeper", "Soft Seat"));
        Coach coach = normalCoach("C01", "Sleeper");

        assertEquals("Sleeper", service.saveCoach(coach).getCoachType());
        verify(coachRepository).save(coach);
    }

    @Test
    void newTypeIsSavedAndAppearsInNextAvailableList() {
        when(coachRepository.findDistinctCoachTypes())
                .thenReturn(List.of("Sleeper", "Soft Seat"))
                .thenReturn(List.of("Sleeper", "Soft Seat", "Premium Cabin"));
        Coach coach = normalCoach("C02", "  Premium Cabin  ");

        assertEquals("Premium Cabin", service.saveCoach(coach).getCoachType());
        assertEquals(List.of("Premium Cabin", "Sleeper", "Soft Seat"), service.getAvailableCoachTypes());
    }

    @Test
    void caseInsensitiveExistingTypeUsesStoredCanonicalValue() {
        when(coachRepository.findDistinctCoachTypes()).thenReturn(List.of("Soft Seat"));
        Coach coach = normalCoach("C03", " SOFT SEAT ");

        assertEquals("Soft Seat", service.saveCoach(coach).getCoachType());
    }

    @Test
    void blankNewTypeCannotBeSaved() {
        Coach coach = normalCoach("C04", "   ");

        assertEquals("Coach type is required.",
                assertThrows(IllegalStateException.class, () -> service.saveCoach(coach)).getMessage());
        verify(coachRepository, never()).save(any());
    }

    @Test
    void coachWithSeatsCannotBeDeleted() {
        Coach coach = new Coach();
        coach.setCoachId(10L);
        when(coachRepository.findById(10L)).thenReturn(java.util.Optional.of(coach));
        when(seatRepository.countByCoach_CoachId(10L)).thenReturn(40L);

        ResourceInUseException error = assertThrows(ResourceInUseException.class,
                () -> service.deleteCoach(10L));

        assertEquals("Cannot delete this coach because it still contains 40 seats.", error.getMessage());
        verify(coachRepository, never()).delete(any());
        verify(coachRepository, never()).flush();
    }

    @Test
    void coachWithoutSeatsOrCompartmentsIsDeleted() {
        Coach coach = new Coach();
        coach.setCoachId(11L);
        when(coachRepository.findById(11L)).thenReturn(java.util.Optional.of(coach));

        service.deleteCoach(11L);

        verify(coachRepository).delete(coach);
        verify(coachRepository).flush();
    }

    @Test
    void missingCoachDoesNotCauseNullPointerException() {
        when(coachRepository.findById(404L)).thenReturn(java.util.Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.deleteCoach(404L));
        assertEquals("Coach not found with ID: 404", error.getMessage());
        verifyNoInteractions(seatRepository);
        verify(coachRepository, never()).delete(any());
    }

    @Test
    void databaseRaceConditionIsConvertedToFriendlyBusinessException() {
        Coach coach = new Coach();
        coach.setCoachId(12L);
        when(coachRepository.findById(12L)).thenReturn(java.util.Optional.of(coach));
        doThrow(new DataIntegrityViolationException("FK_seats_coach raw constraint"))
                .when(coachRepository).flush();

        ResourceInUseException error = assertThrows(ResourceInUseException.class,
                () -> service.deleteCoach(12L));

        assertEquals("Cannot delete this coach because it is still in use.", error.getMessage());
        assertFalse(error.getMessage().contains("FK_"));
    }

    private Coach normalCoach(String number, String type) {
        Train train = new Train();
        train.setTrainId(1L);
        Coach coach = new Coach();
        coach.setTrain(train);
        coach.setCoachNumber(number);
        coach.setCoachType(type);
        coach.setCapacity(1);
        coach.setSleeperCoach(false);
        when(coachRepository.save(coach)).thenAnswer(invocation -> {
            Coach saved = invocation.getArgument(0);
            saved.setCoachId(10L);
            return saved;
        });
        return coach;
    }
}
