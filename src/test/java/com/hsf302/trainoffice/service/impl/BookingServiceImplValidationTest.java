package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.dto.PassengerBookingRequest;
import com.hsf302.trainoffice.repository.*;
import com.hsf302.trainoffice.service.BookingPricingService;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingServiceImplValidationTest {

    private final DiscountPolicyService policies = mock(DiscountPolicyService.class);
    private final BookingServiceImpl service = new BookingServiceImpl(
            mock(BookingRepository.class), mock(PassengerRepository.class), mock(SeatRepository.class),
            mock(StationRepository.class), mock(TicketRepository.class), mock(TripStationRepository.class),
            mock(TrainTripRepository.class), policies, mock(BookingPricingService.class));

    @Test
    void underSixteenStillRequiresRelationshipAndRemapsClientPolicy() {
        PassengerBookingRequest passenger = passengerAged(10, "SENIOR");
        when(policies.resolvePassengerType(passenger.getDateOfBirth())).thenReturn("CHILD");

        IllegalArgumentException error = invokeValidationExpectingFailure(passenger);
        assertEquals("Passenger under 16 must have relationship to booker", error.getMessage());
        assertEquals("CHILD", passenger.getPassengerType());

        passenger.setRelationshipToBooker("Child");
        assertDoesNotThrow(() -> invokeValidation(passenger));
    }

    @Test
    void sixteenAndOlderStillRequiresIdentityNumber() {
        PassengerBookingRequest passenger = passengerAged(16, "CHILD");
        when(policies.resolvePassengerType(passenger.getDateOfBirth())).thenReturn("ADULT");

        IllegalArgumentException error = invokeValidationExpectingFailure(passenger);
        assertEquals("Passengers from 16 years old must have identity number", error.getMessage());
        assertEquals("ADULT", passenger.getPassengerType());

        passenger.setIdentityNumber("ID-16");
        assertDoesNotThrow(() -> invokeValidation(passenger));
    }

    private PassengerBookingRequest passengerAged(int age, String clientType) {
        PassengerBookingRequest passenger = new PassengerBookingRequest();
        passenger.setDateOfBirth(LocalDate.now().minusYears(age));
        passenger.setPassengerType(clientType);
        return passenger;
    }

    private IllegalArgumentException invokeValidationExpectingFailure(PassengerBookingRequest passenger) {
        InvocationTargetException wrapper = assertThrows(InvocationTargetException.class,
                () -> validationMethod().invoke(service, passenger));
        return (IllegalArgumentException) wrapper.getCause();
    }

    private void invokeValidation(PassengerBookingRequest passenger) throws Exception {
        validationMethod().invoke(service, passenger);
    }

    private Method validationMethod() throws NoSuchMethodException {
        Method method = BookingServiceImpl.class.getDeclaredMethod(
                "validatePassengerAgeAndRelationship", PassengerBookingRequest.class);
        method.setAccessible(true);
        return method;
    }
}
