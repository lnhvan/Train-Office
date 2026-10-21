package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.exception.ResourceInUseException;
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
    void postDeleteSuccessPreservesFilterContextAndAddsSuccessFlash() throws Exception {
        mockMvc.perform(post("/admin/seats/7/delete")
                        .param("trainId", "1")
                        .param("coachId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seats?coachId=2&trainId=1"))
                .andExpect(flash().attribute("successMessage", "Seat with ID 7 has been deleted."));

        verify(seatService).deleteSeat(7L);
    }

    @Test
    void blockedDeleteRedirectsWithFriendlyErrorInsteadOfServerError() throws Exception {
        doThrow(new ResourceInUseException(
                "Cannot delete this seat because it is already used by one or more tickets."))
                .when(seatService).deleteSeat(8L);

        mockMvc.perform(post("/admin/seats/8/delete").param("coachId", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/seats?coachId=2"))
                .andExpect(flash().attribute("errorMessage",
                        "Cannot delete this seat because it is already used by one or more tickets."));
    }

    @Test
    void getDeleteIsNotAllowed() throws Exception {
        mockMvc.perform(get("/admin/seats/9/delete"))
                .andExpect(status().isMethodNotAllowed());

        verify(seatService, never()).deleteSeat(anyLong());
    }
}
