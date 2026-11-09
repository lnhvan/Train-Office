package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.BookingStatus;
import com.hsf302.trainoffice.common.enums.PaymentMethod;
import com.hsf302.trainoffice.common.enums.PaymentStatus;
import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.common.enums.UserStatus;
import com.hsf302.trainoffice.entity.Booking;
import com.hsf302.trainoffice.entity.Payment;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.repository.BookingRepository;
import com.hsf302.trainoffice.repository.PaymentRepository;
import com.hsf302.trainoffice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);

    private final UserController controller = new UserController(userService, bookingRepository, paymentRepository);

    @Test
    void showDetailWithExistingUserPopulatesBookingsPaymentsAndStats() {
        Long userId = 2L;
        String email = "vupham3825@gmail.com";

        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName("Vu Pham");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        Booking b1 = Booking.builder()
                .bookingId(101L)
                .bookingNumber("RJ101")
                .bookingStatus(BookingStatus.PAID)
                .bookingDate(LocalDateTime.now().minusDays(1))
                .totalAmount(BigDecimal.valueOf(150000))
                .build();

        Booking b2 = Booking.builder()
                .bookingId(102L)
                .bookingNumber("RJ102")
                .bookingStatus(BookingStatus.PENDING_PAYMENT)
                .bookingDate(LocalDateTime.now())
                .totalAmount(BigDecimal.valueOf(200000))
                .build();

        Payment p1 = Payment.builder()
                .paymentId(UUID.randomUUID())
                .booking(b1)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(150000))
                .paidAt(LocalDateTime.now().minusDays(1))
                .build();

        when(userService.getUserById(userId)).thenReturn(Optional.of(user));
        when(bookingRepository.findByUserOrEmailOrderByBookingDateDesc(userId, email))
                .thenReturn(List.of(b1, b2));
        when(paymentRepository.findByUserOrEmail(userId, email))
                .thenReturn(new ArrayList<>(List.of(p1)));

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.showDetail(userId, model, redirectAttributes);

        assertEquals("users/detail", view);
        assertEquals(user, model.get("user"));
        assertEquals(2, ((List<?>) model.get("bookings")).size());
        assertEquals(1, ((List<?>) model.get("payments")).size());
        assertEquals(1L, model.get("paidBookingsCount"));
        assertEquals(BigDecimal.valueOf(150000), model.get("totalSpent"));
    }

    @Test
    void showDetailWithMissingUserRedirectsToUserList() {
        when(userService.getUserById(999L)).thenReturn(Optional.empty());

        ExtendedModelMap model = new ExtendedModelMap();
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.showDetail(999L, model, redirectAttributes);

        assertEquals("redirect:/admin/users", view);
        assertNotNull(redirectAttributes.getFlashAttributes().get("errorMessage"));
    }
}
