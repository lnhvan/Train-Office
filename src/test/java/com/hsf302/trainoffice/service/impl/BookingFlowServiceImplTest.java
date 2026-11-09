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
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies,
                null); // SeatHoldService (Redis) không dùng trong test lưu thông tin hành khách
        LocalDate dobChild = LocalDate.now().minusYears(10);
        LocalDate dobAdult = LocalDate.now().minusYears(30);
        when(discountPolicies.resolvePassengerType(dobChild)).thenReturn("CHILD");
        when(discountPolicies.resolvePassengerType(dobAdult)).thenReturn("DEFAULT");

        PassengerBookingRequest child = new PassengerBookingRequest();
        child.setDateOfBirth(dobChild);
        child.setPassengerType("SENIOR");

        PassengerBookingRequest adult = new PassengerBookingRequest();
        adult.setDateOfBirth(dobAdult);
        adult.setPassengerType("DEFAULT");

        PassengerInfoForm form = new PassengerInfoForm();
        form.setPassengers(List.of(adult, child));
        BookingSession session = new BookingSession();
        session.setPassengerCount(2);

        service.savePassengerInfo(session, form);

        assertEquals("CHILD", child.getPassengerType());
        assertSame(form, session.getPassengerInfo());
    }

    @Test
    void futureDateDoesNotReachConfirmationSession() {
        DiscountPolicyService discountPolicies = mock(DiscountPolicyService.class);
        BookingFlowServiceImpl service = new BookingFlowServiceImpl(
                mock(TrainTripService.class), mock(TicketService.class), mock(BookingService.class),
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies,
                null); // SeatHoldService (Redis) không dùng trong test lưu thông tin hành khách
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

    @Test
    void minorCannotTravelAloneWithoutAdult() {
        DiscountPolicyService discountPolicies = mock(DiscountPolicyService.class);
        BookingFlowServiceImpl service = new BookingFlowServiceImpl(
                mock(TrainTripService.class), mock(TicketService.class), mock(BookingService.class),
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies,
                null);
        LocalDate dobChild = LocalDate.now().minusYears(12);
        when(discountPolicies.resolvePassengerType(dobChild)).thenReturn("CHILD");

        PassengerBookingRequest child = new PassengerBookingRequest();
        child.setDateOfBirth(dobChild);

        PassengerInfoForm form = new PassengerInfoForm();
        form.setPassengers(List.of(child));
        BookingSession session = new BookingSession();
        session.setPassengerCount(1);

        assertThrows(IllegalArgumentException.class, () -> service.savePassengerInfo(session, form));
    }

    @Test
    void passenger1Under16ThrowsExceptionEvenIfAccompaniedByAdult() {
        DiscountPolicyService discountPolicies = mock(DiscountPolicyService.class);
        BookingFlowServiceImpl service = new BookingFlowServiceImpl(
                mock(TrainTripService.class), mock(TicketService.class), mock(BookingService.class),
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies,
                null);

        LocalDate dobChild = LocalDate.now().minusYears(10);
        LocalDate dobAdult = LocalDate.now().minusYears(30);
        when(discountPolicies.resolvePassengerType(dobChild)).thenReturn("CHILD");
        when(discountPolicies.resolvePassengerType(dobAdult)).thenReturn("ADULT");

        PassengerBookingRequest p1 = new PassengerBookingRequest();
        p1.setDateOfBirth(dobChild); // Passenger 1 is child -> must fail
        PassengerBookingRequest p2 = new PassengerBookingRequest();
        p2.setDateOfBirth(dobAdult);

        PassengerInfoForm form = new PassengerInfoForm();
        form.setPassengers(List.of(p1, p2));
        BookingSession session = new BookingSession();
        session.setPassengerCount(2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.savePassengerInfo(session, form));
        assertTrue(ex.getMessage().contains("Hành khách 1") || ex.getMessage().contains("Passenger 1") || ex.getMessage().contains("err.passenger1MustBeAtLeast16"));
    }

    @Test
    void passenger1AdultAndPassenger2ChildIsValid() {
        DiscountPolicyService discountPolicies = mock(DiscountPolicyService.class);
        BookingFlowServiceImpl service = new BookingFlowServiceImpl(
                mock(TrainTripService.class), mock(TicketService.class), mock(BookingService.class),
                mock(BookingPricingService.class), mock(GroupDiscountPolicyService.class), discountPolicies,
                null);

        LocalDate dobAdult = LocalDate.now().minusYears(30);
        LocalDate dobChild = LocalDate.now().minusYears(10);
        when(discountPolicies.resolvePassengerType(dobAdult)).thenReturn("ADULT");
        when(discountPolicies.resolvePassengerType(dobChild)).thenReturn("CHILD");

        PassengerBookingRequest p1 = new PassengerBookingRequest();
        p1.setDateOfBirth(dobAdult); // Passenger 1 is adult -> valid
        PassengerBookingRequest p2 = new PassengerBookingRequest();
        p2.setDateOfBirth(dobChild); // Passenger 2 can be child -> valid

        PassengerInfoForm form = new PassengerInfoForm();
        form.setPassengers(List.of(p1, p2));
        BookingSession session = new BookingSession();
        session.setPassengerCount(2);

        assertDoesNotThrow(() -> service.savePassengerInfo(session, form));
    }
}
