package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.service.SeatService;
import com.hsf302.trainoffice.service.TrainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        when(seatService.setUnderMaintenance(7L, true)).thenReturn(seat);

        mockMvc.perform(post("/admin/seats/7/maintenance")
                        .param("underMaintenance", "true")
                        .param("trainId", "1")
                        .param("coachId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seats?trainId=1&coachId=2"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(seatService).setUnderMaintenance(7L, true);
    }

    @Test
    void businessErrorRedirectsWithErrorFlashInsteadOfServerError() throws Exception {
        when(seatService.setUnderMaintenance(8L, true))
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

        verify(seatService, never()).setUnderMaintenance(anyLong(), anyBoolean());
    }
}
