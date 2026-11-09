package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.AffectedTicketDto;
import com.hsf302.trainoffice.dto.SeatMaintenanceCheckResult;
import com.hsf302.trainoffice.dto.SeatMaintenanceResult;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.service.SeatService;
import com.hsf302.trainoffice.service.TrainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SeatControllerTest {

    private final SeatService seatService = mock(SeatService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SeatController controller = new SeatController(
                seatService, mock(CoachService.class), mock(TrainService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void markMaintenanceKeepsFilterContextAndAddsSuccessFlash() throws Exception {
        Seat seat = new Seat();
        seat.setSeatNumber("1A");
        SeatMaintenanceResult result = SeatMaintenanceResult.builder()
                .seat(seat)
                .underMaintenance(true)
                .refundedTicketsCount(0)
                .refundedAmount(BigDecimal.ZERO)
                .build();
        when(seatService.updateMaintenance(7L, true, false)).thenReturn(result);

        mockMvc.perform(post("/admin/seats/7/maintenance")
                        .param("underMaintenance", "true")
                        .param("trainId", "1")
                        .param("coachId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seats?trainId=1&coachId=2"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(seatService).updateMaintenance(7L, true, false);
    }

    @Test
    void markMaintenanceWithRefundAddsFlashWithRefundedCount() throws Exception {
        Seat seat = new Seat();
        seat.setSeatNumber("1A");
        SeatMaintenanceResult result = SeatMaintenanceResult.builder()
                .seat(seat)
                .underMaintenance(true)
                .refundedTicketsCount(2)
                .refundedAmount(new BigDecimal("300000"))
                .build();
        when(seatService.updateMaintenance(7L, true, true)).thenReturn(result);

        mockMvc.perform(post("/admin/seats/7/maintenance")
                        .param("underMaintenance", "true")
                        .param("confirmRefund", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seats"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(seatService).updateMaintenance(7L, true, true);
    }

    @Test
    void checkAffectedTicketsReturnsJson() throws Exception {
        SeatMaintenanceCheckResult checkResult = SeatMaintenanceCheckResult.builder()
                .seatId(7L)
                .seatNumber("1A")
                .totalTickets(1)
                .totalRefundAmount(new BigDecimal("150000"))
                .tickets(List.of(AffectedTicketDto.builder().ticketId(10L).ticketCode("TK123").build()))
                .build();
        when(seatService.checkFutureTickets(7L)).thenReturn(checkResult);

        mockMvc.perform(get("/admin/seats/7/affected-tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatNumber").value("1A"))
                .andExpect(jsonPath("$.totalTickets").value(1));

        verify(seatService).checkFutureTickets(7L);
    }

    @Test
    void businessErrorRedirectsWithErrorFlashInsteadOfServerError() throws Exception {
        when(seatService.updateMaintenance(8L, true, false))
                .thenThrow(new IllegalStateException("Seat 1A is retired."));

        mockMvc.perform(post("/admin/seats/8/maintenance")
                        .param("underMaintenance", "true")
                        .param("coachId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seats?coachId=2"))
                .andExpect(flash().attribute("errorMessage", "Seat 1A is retired."));
    }

    @Test
    void getMaintenanceIsNotAllowed() throws Exception {
        mockMvc.perform(get("/admin/seats/9/maintenance"))
                .andExpect(status().isMethodNotAllowed());

        verify(seatService, never()).updateMaintenance(anyLong(), anyBoolean(), anyBoolean());
    }
}
