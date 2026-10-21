package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.Gender;
import com.hsf302.trainoffice.dto.*;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookingPageServiceImplTest {

    private final TrainTripService trips = mock(TrainTripService.class);
    private final BookingFlowService flow = mock(BookingFlowService.class);
    private final DiscountPolicyService discounts = mock(DiscountPolicyService.class);
    private final GroupDiscountPolicyService groupDiscounts = mock(GroupDiscountPolicyService.class);
    private final TicketService tickets = mock(TicketService.class);
    private final BookingPageServiceImpl service = new BookingPageServiceImpl(
            trips, mock(StationService.class), mock(BookingService.class), flow,
            discounts, groupDiscounts, tickets);

    private MockHttpSession httpSession;
    private BookingSession bookingSession;
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(7L).email("user@example.com").fullName("Booker").build();
        httpSession = new MockHttpSession();
        httpSession.setAttribute("currentUser", user);

        bookingSession = new BookingSession();
        bookingSession.setTrainTripId(10L);
        bookingSession.setDepartureStationId(20L);
        bookingSession.setArrivalStationId(30L);
        bookingSession.setPassengerCount(2);
        bookingSession.setSeatIds(List.of(101L, 102L));
        httpSession.setAttribute("bookingSession", bookingSession);

        TripStation departure = new TripStation();
        departure.setStationOrder(1);
        TripStation arrival = new TripStation();
        arrival.setStationOrder(2);
        when(trips.getTripById(10L)).thenReturn(Optional.of(new TrainTrip()));
        when(trips.getValidSegment(10L, 20L, 30L)).thenReturn(new TripSegment(departure, arrival));
        when(tickets.getSeatsForTrip(10L)).thenReturn(List.of(
                Seat.builder().seatId(101L).build(), Seat.builder().seatId(102L).build()));
        when(discounts.getActivePolicies()).thenReturn(List.of());
        when(groupDiscounts.getActivePolicies()).thenReturn(List.of());
    }

    @Test
    void firstRenderAlwaysProvidesCompletePassengerPageModel() {
        PassengerInfoForm initial = formWithPassengers(2);
        when(flow.createPassengerInfoForm(2, user)).thenReturn(initial);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("booking/passenger-info", service.showPassengerInfo(
                httpSession, model, new RedirectAttributesModelMap()));

        assertSame(initial, model.get("passengerInfoForm"));
        assertEquals(2, ((List<?>) model.get("passengers")).size());
        assertNotNull(model.get("bookingSession"));
        assertNotNull(model.get("genders"));
        assertNotNull(model.get("discountPolicies"));
        assertNotNull(model.get("groupDiscountPolicies"));
        assertNotNull(model.get("booker"));
        assertNotNull(model.get("trip"));
        assertNotNull(model.get("segment"));
        assertEquals(2, ((List<?>) model.get("selectedSeats")).size());
    }

    @Test
    void getAfterConfirmationOrRefreshUsesExactSessionPassengerData() {
        PassengerInfoForm saved = formWithPassengers(2);
        saved.getPassengers().get(0).setPassengerType("CHILD");
        saved.getPassengers().get(1).setPassengerType("SENIOR");
        bookingSession.setPassengerInfo(saved);
        when(discounts.resolvePassengerType(saved.getPassengers().get(0).getDateOfBirth())).thenReturn("CHILD");
        when(discounts.resolvePassengerType(saved.getPassengers().get(1).getDateOfBirth())).thenReturn("SENIOR");

        ExtendedModelMap model = new ExtendedModelMap();
        service.showPassengerInfo(httpSession, model, new RedirectAttributesModelMap());

        assertSame(saved, model.get("passengerInfoForm"));
        assertEquals(Gender.FEMALE, saved.getPassengers().get(0).getGender());
        assertEquals("REL-0", saved.getPassengers().get(0).getRelationshipToBooker());
        assertEquals("ID-1", saved.getPassengers().get(1).getIdentityNumber());
        assertEquals(List.of("CHILD", "SENIOR"), saved.getPassengers().stream()
                .map(PassengerBookingRequest::getPassengerType).toList());
    }

    @Test
    void validationErrorKeepsPostedFormAndBuildsCompleteModel() {
        PassengerInfoForm posted = formWithPassengers(2);
        posted.getPassengers().get(0).setPassengerType("FORGED");
        when(discounts.resolvePassengerType(posted.getPassengers().get(0).getDateOfBirth())).thenReturn("CHILD");
        when(discounts.resolvePassengerType(posted.getPassengers().get(1).getDateOfBirth())).thenReturn("ADULT");
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(posted, "passengerInfoForm");
        errors.rejectValue("passengers[0].identityNumber", "invalid", "Invalid identity");
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("booking/passenger-info", service.savePassengerInfo(posted, errors, httpSession,
                model, new RedirectAttributesModelMap()));

        assertSame(posted, model.get("passengerInfoForm"));
        assertEquals("Passenger 0", posted.getPassengers().get(0).getFullName());
        assertEquals("REL-0", posted.getPassengers().get(0).getRelationshipToBooker());
        assertEquals("CHILD", posted.getPassengers().get(0).getPassengerType());
        assertNotNull(model.get("selectedSeats"));
        assertNotNull(model.get("discountPolicies"));
        verify(flow, never()).savePassengerInfo(any(), any());
    }

    @Test
    void completedBookingRemovesOldBookingSession() {
        bookingSession.setPassengerInfo(formWithPassengers(2));
        Booking booking = new Booking();
        booking.setBookingId(99L);
        when(flow.createBooking(bookingSession, user)).thenReturn(booking);

        assertEquals("redirect:/booking/success?bookingId=99", service.createBooking(
                httpSession, new RedirectAttributesModelMap()));
        assertNull(httpSession.getAttribute("bookingSession"));
    }

    private PassengerInfoForm formWithPassengers(int count) {
        PassengerInfoForm form = new PassengerInfoForm();
        form.setContactName("Booker");
        form.setContactPhone("0900000000");
        form.setContactEmail("user@example.com");
        for (int i = 0; i < count; i++) {
            PassengerBookingRequest passenger = new PassengerBookingRequest();
            passenger.setFullName("Passenger " + i);
            passenger.setDateOfBirth(LocalDate.now().minusYears(i == 0 ? 10 : 30));
            passenger.setGender(i == 0 ? Gender.FEMALE : Gender.MALE);
            passenger.setRelationshipToBooker("REL-" + i);
            passenger.setIdentityNumber("ID-" + i);
            form.getPassengers().add(passenger);
        }
        return form;
    }
}
