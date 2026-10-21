package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.UserRole;
import com.hsf302.trainoffice.entity.Refund;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.AdminWalletService;
import com.hsf302.trainoffice.service.RefundService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/refunds")
public class AdminRefundController {

    private final RefundService refundService;
    private final AdminWalletService adminWalletService;

    public AdminRefundController(RefundService refundService,
                                 AdminWalletService adminWalletService) {
        this.refundService = refundService;
        this.adminWalletService = adminWalletService;
    }

    @GetMapping
    public String list(Model model,
                       HttpSession session,
                       RedirectAttributes redirectAttributes) {
        User admin = currentUser(session);

        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("error", "Admin access required.");
            return "redirect:/login";
        }

        model.addAttribute("refunds", refundService.getPendingRefunds());
        model.addAttribute("walletBalance", adminWalletService.getBalance());

        return "refunds/admin-list";
    }

    @PostMapping("/{refundId}/approve")
    public String approve(@PathVariable Long refundId,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        User admin = currentUser(session);

        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("error", "Admin access required.");
            return "redirect:/login";
        }

        try {
            refundService.approveRefund(refundId, admin);
            redirectAttributes.addFlashAttribute("successMessage", "Refund approved successfully.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/refunds";
    }

    @PostMapping("/{refundId}/reject")
    public String reject(@PathVariable Long refundId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        User admin = currentUser(session);

        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("error", "Admin access required.");
            return "redirect:/login";
        }

        try {
            refundService.rejectRefund(refundId, admin);
            redirectAttributes.addFlashAttribute("successMessage", "Refund rejected successfully.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/refunds";
    }

    private User currentUser(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");
        return sessionUser instanceof User user ? user : null;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == UserRole.ADMIN;
    }

    @GetMapping("/{refundId}")
    public String detail(@PathVariable Long refundId,
                         Model model,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        User admin = currentUser(session);

        if (!isAdmin(admin)) {
            redirectAttributes.addFlashAttribute("error", "Admin access required.");
            return "redirect:/login";
        }

        Refund refund = refundService.getRefundById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund request does not exist."));

        model.addAttribute("refund", refund);
        model.addAttribute("walletBalance", adminWalletService.getBalance());

        return "refunds/admin-detail";
    }
}