package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.RefundStatus;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.RefundRepository;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.service.AdminWalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
public class AdminDashboardController {

    private static final int DASHBOARD_PAGE_SIZE = 5;

    private final AdminWalletService adminWalletService;
    private final BookingRepository bookingRepository;
    private final RefundRepository refundRepository;
    private final TrainTripRepository trainTripRepository;

    public AdminDashboardController(AdminWalletService adminWalletService,
                                    BookingRepository bookingRepository,
                                    RefundRepository refundRepository,
                                    TrainTripRepository trainTripRepository) {
        this.adminWalletService = adminWalletService;
        this.bookingRepository = bookingRepository;
        this.refundRepository = refundRepository;
        this.trainTripRepository = trainTripRepository;
    }

    @GetMapping({"/admin", "/admin/dashboard"})
    public String dashboard(Model model,
                            @RequestParam(value = "recentPage", defaultValue = "1") int recentPage,
                            @RequestParam(value = "upcomingPage", defaultValue = "1") int upcomingPage) {
        int safeRecentPage = Math.max(recentPage, 1);
        int safeUpcomingPage = Math.max(upcomingPage, 1);

        BigDecimal walletBalance = adminWalletService.getBalance();

        long totalBookings = bookingRepository.count();
        long paidBookings = bookingRepository.countByBookingStatus(BookingStatus.PAID);
        long pendingRefunds = refundRepository.countByRefundStatus(RefundStatus.PENDING);

        model.addAttribute("adminMenus", adminMenus());
        model.addAttribute("adminMenuUrls", adminMenuUrls());

        model.addAttribute("walletBalance", walletBalance);
        model.addAttribute("totalRevenue", formatVnd(walletBalance));
        model.addAttribute("totalBookings", totalBookings);
        model.addAttribute("occupancyRate", "0%");
        model.addAttribute("pendingRefunds", pendingRefunds);

        model.addAttribute("totalItems", totalBookings);
        model.addAttribute("activeItems", paidBookings);
        model.addAttribute("pendingItems", pendingRefunds);
        model.addAttribute("issueItems", 0);

        Page<Booking> recentBookingPage = bookingRepository.findAllByOrderByBookingDateDesc(
                PageRequest.of(safeRecentPage - 1, DASHBOARD_PAGE_SIZE)
        );

        model.addAttribute("recentBookings", recentBookingPage.getContent());
        model.addAttribute("recentCurrentPage", safeRecentPage);
        model.addAttribute("recentTotalPages", recentBookingPage.getTotalPages());
        model.addAttribute("recentTotalItems", recentBookingPage.getTotalElements());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next30Days = LocalDate.now().plusDays(30).atTime(23, 59, 59);

        Page<TrainTrip> upcomingTripPage = trainTripRepository
                .findByStatusAndDepartureTimeGreaterThanEqualAndDepartureTimeLessThanOrderByDepartureTimeAsc(
                        TripStatus.SCHEDULED,
                        now,
                        next30Days,
                        PageRequest.of(safeUpcomingPage - 1, DASHBOARD_PAGE_SIZE)
                );

        model.addAttribute("trips", upcomingTripPage.getContent());
        model.addAttribute("upcomingCurrentPage", safeUpcomingPage);
        model.addAttribute("upcomingTotalPages", upcomingTripPage.getTotalPages());
        model.addAttribute("upcomingTripsTotal", upcomingTripPage.getTotalElements());

        model.addAttribute("chartLabels", List.of("Revenue"));
        model.addAttribute("chartData", List.of(walletBalance));

        return "admin/dashboard";
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        return formatter.format(amount) + " VND";
    }

    private List<Map<String, String>> adminMenus() {
        return List.of(
                Map.of("key", "users", "label", "Users", "icon", " fas fa-users"),
                Map.of("key", "passengers", "label", "Passengers", "icon", " fas fa-user-friends"),
                Map.of("key", "stations", "label", "Stations", "icon", " fas fa-map-marker-alt"),
                Map.of("key", "routes", "label", "Routes", "icon", " fas fa-route"),
                Map.of("key", "trains", "label", "Trains", "icon", " fas fa-train"),
                Map.of("key", "coaches", "label", "Coaches", "icon", " fas fa-subway"),
                Map.of("key", "seats", "label", "Seats", "icon", " fas fa-chair"),
                Map.of("key", "trips", "label", "Trips", "icon", " fas fa-calendar-alt"),
                Map.of("key", "payments", "label", "Payments", "icon", " fas fa-credit-card"),
                Map.of("key", "refunds", "label", "Refunds", "icon", " fas fa-undo"),
                Map.of("key", "groupDiscounts", "label", "Group Discounts", "icon", " fas fa-users"),
                Map.of("key", "discounts", "label", "Discount Policies", "icon", " fas fa-percent")
        );
    }

    private Map<String, String> adminMenuUrls() {
        return Map.ofEntries(
                Map.entry("users", "/admin/users"),
                Map.entry("passengers", "/admin/passengers"),
                Map.entry("stations", "/admin/stations"),
                Map.entry("routes", "/admin/routes"),
                Map.entry("trains", "/admin/trains"),
                Map.entry("coaches", "/admin/coaches"),
                Map.entry("seats", "/admin/seats"),
                Map.entry("trips", "/admin/trips"),
                Map.entry("discounts", "/admin/discount-policies"),
                Map.entry("payments", "/admin/payments"),
                Map.entry("refunds", "/admin/refunds"),
                Map.entry("groupDiscounts", "/admin/group-discount-policies")
        );
    }
}