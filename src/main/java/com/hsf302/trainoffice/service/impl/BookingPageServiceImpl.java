package com.hsf302.trainoffice.service.impl;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.Gender;
import com.hsf302.trainoffice.dto.BookingConfirmationView;
import com.hsf302.trainoffice.dto.BookingSession;
import com.hsf302.trainoffice.dto.PassengerInfoForm;
import com.hsf302.trainoffice.dto.SeatSelectionForm;
import com.hsf302.trainoffice.dto.TripSearchForm;
import com.hsf302.trainoffice.dto.TripSearchResult;
import com.hsf302.trainoffice.dto.TripSegment;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.BookingFlowService;
import com.hsf302.trainoffice.service.BookingPageService;
import com.hsf302.trainoffice.service.BookingService;
import com.hsf302.trainoffice.service.StationService;
import com.hsf302.trainoffice.service.TicketService;
import com.hsf302.trainoffice.service.TrainTripService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.hsf302.trainoffice.service.DiscountPolicyService;
import com.hsf302.trainoffice.service.GroupDiscountPolicyService;
import com.hsf302.trainoffice.entity.Seat;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;

@Service
public class BookingPageServiceImpl implements BookingPageService {

    private static final String BOOKING_SESSION_KEY = "bookingSession";

    private final TrainTripService trainTripService;
    private final StationService stationService;
    private final BookingService bookingService;
    private final BookingFlowService bookingFlowService;
    private final TicketService ticketService;
    private final DiscountPolicyService discountPolicyService;
    private final GroupDiscountPolicyService groupDiscountPolicyService;


    public BookingPageServiceImpl(TrainTripService trainTripService,
                                  StationService stationService,
                                  BookingService bookingService,
                                  BookingFlowService bookingFlowService,
                                  DiscountPolicyService discountPolicyService,
                                  GroupDiscountPolicyService groupDiscountPolicyService,
                                  TicketService ticketService) {
        this.trainTripService = trainTripService;
        this.stationService = stationService;
        this.bookingService = bookingService;
        this.bookingFlowService = bookingFlowService;
        this.ticketService = ticketService;
        this.discountPolicyService = discountPolicyService;
        this.groupDiscountPolicyService = groupDiscountPolicyService;
    }

    @Override
    public String showSearchPage(Model model) {
        if (!model.containsAttribute("tripSearchForm")) {
            model.addAttribute("tripSearchForm", new TripSearchForm());
        }

        model.addAttribute("stations", stationService.getAllStations());

        return "booking/search";
    }

    @Override
    public String showTripResults(TripSearchForm form,
                                  BindingResult bindingResult,
                                  Model model) {
        model.addAttribute("stations", stationService.getAllStations());

        if (bindingResult.hasErrors()) {
            model.addAttribute("trips", List.of());
            return "booking/trips";
        }

        if (Boolean.TRUE.equals(form.getRoundTrip())) {
            return showRoundTripResults(form, model);
        }

        try {
            model.addAttribute("trips", trainTripService.searchTrips(form));
            model.addAttribute("tripSearchForm", form);

        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("trips", List.of());
        }

        return "booking/trips";
    }

    @Override
    public String showSeatSelection(Long tripId,
                                    Long departureStationId,
                                    Long arrivalStationId,
                                    int passengerCount,
                                    HttpSession session,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        if (currentUser(session) == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để mua vé.");
            return "redirect:/login";
        }

        try {
            TripSegment segment = trainTripService.getValidSegment(
                    tripId,
                    departureStationId,
                    arrivalStationId
            );

            SeatSelectionForm form = new SeatSelectionForm();
            form.setTrainTripId(tripId);
            form.setDepartureStationId(departureStationId);
            form.setArrivalStationId(arrivalStationId);
            form.setPassengerCount(passengerCount);

            model.addAttribute("seatSelectionForm", form);
            model.addAttribute("trip", trainTripService.getTripById(tripId).orElseThrow());
            model.addAttribute("segment", segment);
            model.addAttribute("seats", bookingFlowService.getSeatAvailability(
                    tripId,
                    departureStationId,
                    arrivalStationId
            ));

            return "booking/seat-selection";

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/search";
        }
    }

    @Override
    public String selectSeats(SeatSelectionForm form,
                              BindingResult bindingResult,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        if (currentUser(session) == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để mua vé.");
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()
                || form.getSeatIds() == null
                || form.getSeatIds().size() != form.getPassengerCount()) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Please select exactly " + form.getPassengerCount() + " seats"
            );
            return redirectToSeatSelection(form);
        }

        try {
            BookingSession previousSession = getBookingSession(session);

            BookingSession bookingSession = bookingFlowService.buildBookingSession(form);

            if (canReusePassengerInfo(previousSession, bookingSession)) {
                bookingSession.setPassengerInfo(previousSession.getPassengerInfo());
                bookingSession.setSelectedGroupDiscountPolicyId(previousSession.getSelectedGroupDiscountPolicyId());
            }

            session.setAttribute(BOOKING_SESSION_KEY, bookingSession);

            return "redirect:/booking/passenger-info";

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return redirectToSeatSelection(form);
        }
    }

    @Override
    public String showPassengerInfo(HttpSession session,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tiếp tục mua vé.");
            return "redirect:/login";
        }

        BookingSession bookingSession = getBookingSession(session);

        if (bookingSession == null) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Please select seats before entering passenger information"
            );
            return "redirect:/booking/search";
        }

        PassengerInfoForm form;
        if (!model.containsAttribute("passengerInfoForm")) {
            form = bookingSession.getPassengerInfo() == null
                    ? bookingFlowService.createPassengerInfoForm(
                    bookingSession.getPassengerCount(),
                    user
            )
                    : bookingSession.getPassengerInfo();

            model.addAttribute("passengerInfoForm", form);
        } else {
            form = (PassengerInfoForm) model.getAttribute("passengerInfoForm");
        }

        normalizePassengerTypesForDisplay(form);
        addPassengerInfoModel(model, bookingSession, form, user);
        return "booking/passenger-info";
    }

    @Override
    public String savePassengerInfo(PassengerInfoForm form,
                                    BindingResult bindingResult,
                                    HttpSession session,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        if (currentUser(session) == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tiếp tục mua vé.");
            return "redirect:/login";
        }

        BookingSession bookingSession = getBookingSession(session);

        if (bookingSession == null) {
            return "redirect:/booking/search";
        }

        if (bindingResult.hasErrors()
                || form.getPassengers() == null
                || form.getPassengers().size() != bookingSession.getPassengerCount()) {
            normalizePassengerTypesForDisplay(form);
            addPassengerInfoModel(model, bookingSession, form, currentUser(session));
            return "booking/passenger-info";
        }

        try {
            bookingFlowService.savePassengerInfo(bookingSession, form);
            session.setAttribute(BOOKING_SESSION_KEY, bookingSession);

            return "redirect:/booking/confirmation";

        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            normalizePassengerTypesForDisplay(form);
            addPassengerInfoModel(model, bookingSession, form, currentUser(session));
            return "booking/passenger-info";
        }
    }

    private void normalizePassengerTypesForDisplay(PassengerInfoForm form) {
        if (form == null || form.getPassengers() == null) {
            return;
        }
        form.getPassengers().forEach(passenger -> {
            LocalDate dateOfBirth = passenger.getDateOfBirth();
            if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now())) {
                passenger.setPassengerType("DEFAULT");
                return;
            }
            passenger.setPassengerType(discountPolicyService.resolvePassengerType(dateOfBirth));
        });
    }

    private void addPassengerInfoModel(Model model,
                                       BookingSession bookingSession,
                                       PassengerInfoForm form,
                                       User user) {
        model.addAttribute("passengerInfoForm", form);
        model.addAttribute("passengers", form == null || form.getPassengers() == null
                ? List.of() : form.getPassengers());
        model.addAttribute("genders", Gender.values());
        model.addAttribute("bookingSession", bookingSession);
        model.addAttribute("discountPolicies", discountPolicyService.getActivePolicies());
        model.addAttribute("groupDiscountPolicies", groupDiscountPolicyService.getActivePolicies());
        model.addAttribute("selectedGroupDiscountPolicyId", bookingSession.getSelectedGroupDiscountPolicyId());
        model.addAttribute("booker", user);

        model.addAttribute("trip", trainTripService.getTripById(bookingSession.getTrainTripId())
                .orElseThrow(() -> new IllegalArgumentException("Train trip does not exist")));
        model.addAttribute("segment", trainTripService.getValidSegment(
                bookingSession.getTrainTripId(),
                bookingSession.getDepartureStationId(),
                bookingSession.getArrivalStationId()));

        Map<Long, Seat> seatsById = ticketService.getSeatsForTrip(bookingSession.getTrainTripId()).stream()
                .collect(Collectors.toMap(Seat::getSeatId, Function.identity()));
        List<Seat> selectedSeats = bookingSession.getSeatIds().stream()
                .map(seatsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        model.addAttribute("selectedSeats", selectedSeats);
    }

    @Override
    public String applyGroupDiscount(Long groupDiscountPolicyId,
                                     HttpSession session,
                                     RedirectAttributes redirectAttributes) {
        if (currentUser(session) == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tiếp tục mua vé.");
            return "redirect:/login";
        }

        BookingSession bookingSession = getBookingSession(session);

        if (bookingSession == null || bookingSession.getPassengerInfo() == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete booking information first");
            return "redirect:/booking/search";
        }

        try {
            bookingFlowService.applyGroupDiscount(bookingSession, groupDiscountPolicyId);
            session.setAttribute(BOOKING_SESSION_KEY, bookingSession);

            if (groupDiscountPolicyId == null) {
                redirectAttributes.addFlashAttribute("successMessage", "Group discount removed.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Group discount applied.");
            }

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/booking/confirmation";
    }

    @Override
    public String showConfirmation(HttpSession session,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (currentUser(session) == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để xác nhận booking.");
            return "redirect:/login";
        }

        BookingSession bookingSession = getBookingSession(session);

        if (bookingSession == null || bookingSession.getPassengerInfo() == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete booking information first");
            return "redirect:/booking/search";
        }

        try {
            BookingConfirmationView confirmation = bookingFlowService.buildConfirmation(bookingSession);
            addConfirmationModel(confirmation, model);

            return "booking/confirmation";

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/search";
        }
    }

    @Override
    public String createBooking(HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để tạo booking.");
            return "redirect:/login";
        }

        BookingSession bookingSession = getBookingSession(session);

        if (bookingSession == null || bookingSession.getPassengerInfo() == null) {
            redirectAttributes.addFlashAttribute("error", "Please complete booking information first");
            return "redirect:/booking/search";
        }

        try {
            Booking booking = bookingFlowService.createBooking(bookingSession, user);

            session.removeAttribute(BOOKING_SESSION_KEY);

            return "redirect:/booking/success?bookingId=" + booking.getBookingId();

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/confirmation";
        }
    }

    @Override
    public String showSuccess(Long bookingId,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để xem booking.");
            return "redirect:/login";
        }

        if (bookingId != null) {
            try {
                Booking booking = bookingService.getBookingById(bookingId);

                if (!canAccessBooking(booking, user)) {
                    redirectAttributes.addFlashAttribute("error", "Booking does not belong to current user");
                    return "redirect:/booking/history";
                }

                model.addAttribute("booking", booking);

            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/booking/history";
            }
        }

        return "booking/success";
    }

    @Override
    public String showHistory(String status,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Please log in to view your bookings.");
            return "redirect:/login";
        }

        List<Booking> bookings = bookingService.getBookingsForUser(user);

        String selectedStatus = normalizeStatus(status);

        if (selectedStatus != null) {
            try {
                BookingStatus bookingStatus = BookingStatus.valueOf(selectedStatus);

                bookings = bookings.stream()
                        .filter(booking -> booking.getBookingStatus() == bookingStatus)
                        .toList();

            } catch (IllegalArgumentException ex) {
                selectedStatus = null;
            }
        }

        model.addAttribute("recentBookings", bookings);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("bookingStatuses", BookingStatus.values());

        return "booking/history";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }

        return status.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String showDetail(Long bookingId,
                             HttpSession session,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Please log in to view booking detail");
            return "redirect:/login";
        }

        try {
            Booking booking = bookingService.getBookingById(bookingId);

            if (!canAccessBooking(booking, user)) {
                redirectAttributes.addFlashAttribute("error", "Booking does not belong to current user");
                return "redirect:/booking/history";
            }

            model.addAttribute("booking", booking);
            model.addAttribute("passengers", bookingService.getPassengersForBooking(bookingId));
            model.addAttribute("tickets", ticketService.getTicketsByBookingId(bookingId));

            return "booking/detail";

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/booking/history";
        }
    }

    @Override
    public String cancelBooking(Long bookingId,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "Please log in to cancel booking");
            return "redirect:/login";
        }

        try {
            bookingService.cancelPendingBooking(bookingId, user);
            redirectAttributes.addFlashAttribute("successMessage", "Booking cancelled successfully");

        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/booking/" + bookingId;
    }

    @Override
    public String loginRequired(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để mua vé.");
        return "redirect:/login";
    }

    private String showRoundTripResults(TripSearchForm form,
                                        Model model) {
        if (form.getReturnDate() == null) {
            model.addAttribute("error", "Please select a return date for round trip.");
            model.addAttribute("tripSearchForm", form);
            model.addAttribute("stations", stationService.getAllStations());

            return "booking/search";
        }

        if (form.getReturnDate().isBefore(form.getDepartureDate())) {
            model.addAttribute("error", "Return date must be after or equal to departure date.");
            model.addAttribute("tripSearchForm", form);
            model.addAttribute("stations", stationService.getAllStations());

            return "booking/search";
        }

        List<TripSearchResult> outboundTrips = List.of();
        List<TripSearchResult> inboundTrips = List.of();

        String outboundError = null;
        String inboundError = null;

        try {
            outboundTrips = trainTripService.searchTrips(form);

        } catch (IllegalArgumentException ex) {
            outboundError = ex.getMessage();
        }

        try {
            TripSearchForm returnForm = buildReturnTripSearchForm(form);
            inboundTrips = trainTripService.searchTrips(returnForm);

        } catch (IllegalArgumentException ex) {
            inboundError = ex.getMessage();
        }

        model.addAttribute("tripSearchForm", form);

        model.addAttribute("outboundTrips", outboundTrips);
        model.addAttribute("inboundTrips", inboundTrips);

        model.addAttribute("outboundError", outboundError);
        model.addAttribute("inboundError", inboundError);

        model.addAttribute("departureDate", form.getDepartureDate());
        model.addAttribute("returnDate", form.getReturnDate());

        return "booking/round-trip-results";
    }

    private TripSearchForm buildReturnTripSearchForm(TripSearchForm form) {
        TripSearchForm returnForm = new TripSearchForm();

        returnForm.setDepartureStationId(form.getArrivalStationId());
        returnForm.setArrivalStationId(form.getDepartureStationId());
        returnForm.setDepartureDate(form.getReturnDate());
        returnForm.setPassengerCount(form.getPassengerCount());
        returnForm.setRoundTrip(false);

        return returnForm;
    }

    private void addConfirmationModel(BookingConfirmationView confirmation,
                                      Model model) {
        model.addAttribute("bookingSession", confirmation.getBookingSession());
        model.addAttribute("passengerInfo", confirmation.getPassengerInfo());
        model.addAttribute("trip", confirmation.getTrip());
        model.addAttribute("segment", confirmation.getSegment());
        model.addAttribute("selectedSeats", confirmation.getSelectedSeats());
        model.addAttribute("totalAmount", confirmation.getTotalAmount());
        model.addAttribute("priceSummary", confirmation.getPriceSummary());
        model.addAttribute("fareBreakdownItems", confirmation.getPriceSummary().getPassengerItems());
        model.addAttribute("availableGroupDiscountPolicies", confirmation.getAvailableGroupDiscountPolicies());
        model.addAttribute("selectedGroupDiscountPolicyId", confirmation.getBookingSession().getSelectedGroupDiscountPolicyId());
    }

    private BookingSession getBookingSession(HttpSession session) {
        Object value = session.getAttribute(BOOKING_SESSION_KEY);
        return value instanceof BookingSession bookingSession ? bookingSession : null;
    }

    private User currentUser(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");
        return sessionUser instanceof User user ? user : null;
    }

    private boolean canAccessBooking(Booking booking, User user) {
        if (booking == null || user == null) {
            return false;
        }

        if (booking.getUser() == null) {
            return false;
        }

        return booking.getUser().getUserId().equals(user.getUserId());
    }

    private String redirectToSeatSelection(SeatSelectionForm form) {
        return "redirect:/booking/" + form.getTrainTripId() + "/seats"
                + "?departureStationId=" + form.getDepartureStationId()
                + "&arrivalStationId=" + form.getArrivalStationId()
                + "&passengerCount=" + form.getPassengerCount();
    }

    private boolean canReusePassengerInfo(BookingSession oldSession, BookingSession newSession) {
        if (oldSession == null || newSession == null) {
            return false;
        }

        if (oldSession.getPassengerInfo() == null) {
            return false;
        }

        if (oldSession.getPassengerCount() != newSession.getPassengerCount()) {
            return false;
        }

        if (!oldSession.getTrainTripId().equals(newSession.getTrainTripId())) {
            return false;
        }

        if (!oldSession.getDepartureStationId().equals(newSession.getDepartureStationId())) {
            return false;
        }

        return oldSession.getArrivalStationId().equals(newSession.getArrivalStationId());
    }
}
