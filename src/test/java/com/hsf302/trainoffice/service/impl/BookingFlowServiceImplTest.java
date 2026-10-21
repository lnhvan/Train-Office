package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.BookingSession;
import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.service.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingFlowServiceImplTest {

    @Test
    void savePassengerInfoOverwritesClientPolicyAndStoresResolvedPolicy() {
        DiscountPolicyService discountPolicies = mock(DiscountPolicyService.class);
        BookingFlowServiceImpl service = new BookingFlowServiceImpl(
                mock(TrainTripService.class), mock(TicketService.class), mock(BookingService.class),
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies);
        LocalDate dob = LocalDate.now().minusYears(10);
        when(discountPolicies.resolvePassengerType(dob)).thenReturn("CHILD");

        PassengerBookingRequest passenger = new PassengerBookingRequest();
        passenger.setDateOfBirth(dob);
        passenger.setPassengerType("SENIOR");
        PassengerInfoForm form = new PassengerInfoForm();
        form.setPassengers(List.of(passenger));
        BookingSession session = new BookingSession();
        session.setPassengerCount(1);

        service.savePassengerInfo(session, form);

        assertEquals("CHILD", passenger.getPassengerType());
        assertSame(form, session.getPassengerInfo());
    }

    @Test
    void futureDateDoesNotReachConfirmationSession() {
        DiscountPolicyService discountPolicies = mock(DiscountPolicyService.class);
        BookingFlowServiceImpl service = new BookingFlowServiceImpl(
                mock(TrainTripService.class), mock(TicketService.class), mock(BookingService.class),
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies);
        LocalDate future = LocalDate.now().plusDays(1);
        when(discountPolicies.resolvePassengerType(future))
                .thenThrow(new IllegalArgumentException("Passenger date of birth cannot be in the future"));
        PassengerBookingRequest passenger = new PassengerBookingRequest();
        passenger.setDateOfBirth(future);
        PassengerInfoForm form = new PassengerInfoForm();
        form.setPassengers(List.of(passenger));
        BookingSession session = new BookingSession();
        session.setPassengerCount(1);

        assertThrows(IllegalArgumentException.class, () -> service.savePassengerInfo(session, form));
        assertNull(session.getPassengerInfo());
    }
}
