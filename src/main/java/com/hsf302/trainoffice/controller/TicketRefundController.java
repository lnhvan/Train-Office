package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.RefundRequestForm;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.RefundService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TicketRefundController {

    private final RefundService refundService;

    public TicketRefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping("/tickets/{ticketId}/refund")
    public String showRefundForm(@PathVariable Long ticketId,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Please log in to request a refund."
            );
            return "redirect:/login";
        }

        if (!model.containsAttribute("refundRequestForm")) {
            RefundRequestForm form = new RefundRequestForm();

            form.setCustomerName(user.getFullName());
            form.setCustomerEmail(user.getEmail());

            /*
             * User entity hiện tại không có getPhoneNumber(),
             * nên không set phone ở đây.
             * Khách sẽ tự nhập Customer Phone trong refund form.
             */
            form.setCustomerPhone("");

            model.addAttribute("refundRequestForm", form);
        }

        model.addAttribute("ticketId", ticketId);

        return "refunds/customer-request";
    }

    @PostMapping("/tickets/{ticketId}/refund")
    public String requestRefund(@PathVariable Long ticketId,
                                @Valid @ModelAttribute("refundRequestForm") RefundRequestForm form,
                                BindingResult bindingResult,
                                HttpSession session,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        User user = currentUser(session);

        if (user == null) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Please log in to request a refund."
            );
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("ticketId", ticketId);
            return "refunds/customer-request";
        }

        try {
            refundService.createRefundRequest(ticketId, user, form);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Refund request has been sent successfully. Please wait for admin approval."
            );

            return "redirect:/tickets";

        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("ticketId", ticketId);
            return "refunds/customer-request";

        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Cannot create refund request. Please try again.");
            model.addAttribute("ticketId", ticketId);
            return "refunds/customer-request";
        }
    }

    private User currentUser(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");
        return sessionUser instanceof User user ? user : null;
    }
}