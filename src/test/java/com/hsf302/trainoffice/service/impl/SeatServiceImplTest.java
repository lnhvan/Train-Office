package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatServiceImplTest {

    private final SeatRepository seatRepository = mock(SeatRepository.class);
    private final TicketRepository ticketRepository = mock(TicketRepository.class);
    private final SeatServiceImpl service = new SeatServiceImpl(seatRepository, ticketRepository);

    @Test
    void unusedSeatIsDeletedAndFlushed() {
        Seat seat = seat(1L);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));

        service.deleteSeat(1L);

        verify(ticketRepository).existsBySeat_SeatId(1L);
        verify(seatRepository).delete(seat);
        verify(seatRepository).flush();
    }

    @Test
    void seatReferencedByAnyTicketCannotBeDeleted() {
        Seat seat = seat(2L);
        when(seatRepository.findById(2L)).thenReturn(Optional.of(seat));
        when(ticketRepository.existsBySeat_SeatId(2L)).thenReturn(true);

        ResourceInUseException error = assertThrows(ResourceInUseException.class,
                () -> service.deleteSeat(2L));

        assertEquals("Cannot delete this seat because it is already used by one or more tickets.",
                error.getMessage());
        verify(seatRepository, never()).delete(any());
        verify(seatRepository, never()).flush();
    }

    @Test
    void missingSeatIsHandledWithoutNullPointerException() {
        when(seatRepository.findById(404L)).thenReturn(Optional.empty());

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.deleteSeat(404L));

        assertEquals("Seat not found with ID: 404", error.getMessage());
        verifyNoInteractions(ticketRepository);
        verify(seatRepository, never()).delete(any());
    }

    @Test
    void databaseRaceConditionIsConvertedToFriendlyBusinessException() {
        Seat seat = seat(3L);
        when(seatRepository.findById(3L)).thenReturn(Optional.of(seat));
        doThrow(new DataIntegrityViolationException("FK_tickets_seat raw constraint"))
                .when(seatRepository).flush();

        ResourceInUseException error = assertThrows(ResourceInUseException.class,
                () -> service.deleteSeat(3L));

        assertEquals("Cannot delete this seat because it is still in use.", error.getMessage());
        assertFalse(error.getMessage().contains("FK_"));
    }

    @Test
    void cancelledTicketRecordStillBlocksSeatDeletion() {
        Seat seat = seat(4L);
        when(seatRepository.findById(4L)).thenReturn(Optional.of(seat));
        // The repository method intentionally has no TicketStatus filter.
        when(ticketRepository.existsBySeat_SeatId(4L)).thenReturn(true);

        assertThrows(ResourceInUseException.class, () -> service.deleteSeat(4L));
        verify(seatRepository, never()).delete(any());
    }

    private Seat seat(Long id) {
        Seat seat = new Seat();
        seat.setSeatId(id);
        seat.setSeatNumber("A" + id);
        return seat;
    }
}
