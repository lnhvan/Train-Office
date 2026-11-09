package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.security.CurrentUser;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.RefundQuote;
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
                    I18n.t("msg.refund.loginRequired")
            );
            return "redirect:/login";
        }

        // Kiểm tra vé còn hoàn được và tính trước số tiền hoàn để khách thấy trước khi gửi.
        RefundQuote quote;
        try {
            quote = refundService.quoteRefund(ticketId, user);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/tickets";
        }

        model.addAttribute("refundQuote", quote);

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
                    I18n.t("msg.refund.loginRequired")
            );
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("ticketId", ticketId);
            addQuote(model, ticketId, user);
            return "refunds/customer-request";
        }

        try {
            refundService.createRefundRequest(ticketId, user, form);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    I18n.t("msg.refund.sent")
            );

            return "redirect:/tickets";

        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("ticketId", ticketId);
            addQuote(model, ticketId, user);
            return "refunds/customer-request";

        } catch (Exception ex) {
            model.addAttribute("errorMessage", I18n.t("msg.refund.createError"));
            model.addAttribute("ticketId", ticketId);
            addQuote(model, ticketId, user);
            return "refunds/customer-request";
        }
    }

    private void addQuote(Model model, Long ticketId, User user) {
        try {
            model.addAttribute("refundQuote", refundService.quoteRefund(ticketId, user));
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Lỗi đã hiện qua errorMessage; không có báo giá thì trang chỉ ẩn khung số tiền.
        }
    }

    /** Người dùng đăng nhập (JWT) của request hiện tại; null nếu chưa đăng nhập. */
    private User currentUser(HttpSession session) {
        return CurrentUser.get();
    }
}