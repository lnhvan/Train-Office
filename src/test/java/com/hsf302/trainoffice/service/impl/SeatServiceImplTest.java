package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.TicketStatus;
import com.hsf302.trainoffice.dto.SeatMaintenanceCheckResult;
import com.hsf302.trainoffice.dto.SeatMaintenanceResult;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.event.SeatHoldReleaseEvent;
import com.hsf302.trainoffice.repository.SeatRepository;
import com.hsf302.trainoffice.repository.TicketRepository;
import com.hsf302.trainoffice.service.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void updateMaintenance_WithActiveTicketsAndNoConfirm_ThrowsIllegalStateException() {
        Seat seat = seat(1L, true);
        seat.setSeatNumber("05");
        TicketRepository ticketRepository = mock(TicketRepository.class);
        RefundService refundService = mock(RefundService.class);
        SeatServiceImpl fullService = new SeatServiceImpl(seatRepository, ticketRepository, refundService, null);

        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        Ticket ticket = new Ticket();
        ticket.setTicketId(10L);
        ticket.setTicketStatus(TicketStatus.CONFIRMED);
        when(ticketRepository.findFutureActiveTicketsBySeat(eq(1L), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of(ticket));

        assertThrows(IllegalStateException.class, () -> fullService.updateMaintenance(1L, true, false));
        verify(refundService, never()).refundTicketsForSeatMaintenance(any(), any());
    }

    @Test
    void updateMaintenance_WithActiveTicketsAndConfirmed_ExecutesRefundAndSetsMaintenance() {
        Seat seat = seat(1L, true);
        seat.setSeatNumber("05");
        TicketRepository ticketRepository = mock(TicketRepository.class);
        RefundService refundService = mock(RefundService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SeatServiceImpl fullService = new SeatServiceImpl(seatRepository, ticketRepository, refundService, eventPublisher);

        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));
        when(seatRepository.save(seat)).thenReturn(seat);

        Booking booking = new Booking();
        booking.setBookingId(99L);
        Ticket ticket = new Ticket();
        ticket.setTicketId(10L);
        ticket.setTicketStatus(TicketStatus.CONFIRMED);
        ticket.setBooking(booking);

        when(ticketRepository.findFutureActiveTicketsBySeat(eq(1L), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of(ticket));
        when(refundService.refundTicketsForSeatMaintenance(eq(seat), anyList()))
                .thenReturn(new TripCancellationResult(1, new BigDecimal("200000"), 0));

        SeatMaintenanceResult result = fullService.updateMaintenance(1L, true, true);

        assertTrue(result.isUnderMaintenance());
        assertEquals(1, result.getRefundedTicketsCount());
        assertEquals(new BigDecimal("200000"), result.getRefundedAmount());
        verify(refundService).refundTicketsForSeatMaintenance(eq(seat), anyList());
        verify(eventPublisher).publishEvent(any(SeatHoldReleaseEvent.class));
    }

    @Test
    void checkFutureTickets_ReturnsCorrectCheckResult() {
        Seat seat = seat(1L, true);
        seat.setSeatNumber("05");
        TicketRepository ticketRepository = mock(TicketRepository.class);
        SeatServiceImpl fullService = new SeatServiceImpl(seatRepository, ticketRepository, null, null);

        when(seatRepository.findById(1L)).thenReturn(Optional.of(seat));

        Booking booking = new Booking();
        booking.setBookingNumber("RJ12345");

        Ticket ticket = new Ticket();
        ticket.setTicketId(10L);
        ticket.setTicketCode("TK999");
        ticket.setTicketPrice(new BigDecimal("150000"));
        ticket.setTicketStatus(TicketStatus.CONFIRMED);
        ticket.setBooking(booking);

        when(ticketRepository.findFutureActiveTicketsBySeat(eq(1L), any(LocalDateTime.class), anyCollection()))
                .thenReturn(List.of(ticket));

        SeatMaintenanceCheckResult checkResult = fullService.checkFutureTickets(1L);

        assertEquals(1L, checkResult.getSeatId());
        assertEquals("05", checkResult.getSeatNumber());
        assertEquals(1, checkResult.getTotalTickets());
        assertEquals(1, checkResult.getConfirmedCount());
        assertEquals(new BigDecimal("150000"), checkResult.getTotalRefundAmount());
        assertEquals(1, checkResult.getTickets().size());
        assertEquals("TK999", checkResult.getTickets().get(0).getTicketCode());
    }

    private Seat seat(Long id, boolean active) {
        Seat seat = new Seat();
        seat.setSeatId(id);
        seat.setActive(active);
        seat.setUnderMaintenance(false);
        return seat;
    }
}
