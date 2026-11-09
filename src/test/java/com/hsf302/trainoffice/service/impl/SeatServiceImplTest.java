package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.repository.SeatRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatServiceImplTest {

    private final SeatRepository seatRepository = mock(SeatRepository.class);
    private final SeatServiceImpl service = new SeatServiceImpl(seatRepository);

    @Test
    void activeSeatCanBeMarkedUnderMaintenanceAndReopened() {
        Seat seat = seat(1L, true);
        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(seat)).thenReturn(seat);

        assertTrue(service.setUnderMaintenance(1L, true).getUnderMaintenance());
        assertFalse(seat.isBookable());

        assertFalse(service.setUnderMaintenance(1L, false).getUnderMaintenance());
        assertTrue(seat.isBookable());
        verify(seatRepository, times(2)).save(seat);
    }

    @Test
    void retiredSeatCannotChangeMaintenanceStatus() {
        Seat seat = seat(2L, false);
        when(seatRepository.findById(2L)).thenReturn(Optional.of(seat));

        assertThrows(IllegalStateException.class, () -> service.setUnderMaintenance(2L, true));
        verify(seatRepository, never()).save(any());
    }

    @Test
    void missingSeatIsReported() {
        when(seatRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.setUnderMaintenance(404L, true));
        verify(seatRepository, never()).save(any());
    }

    private Seat seat(Long id, boolean active) {
        Seat seat = new Seat();
        seat.setSeatId(id);
        seat.setSeatNumber("1A");
        seat.setActive(active);
        seat.setUnderMaintenance(false);
        return seat;
    }
}
