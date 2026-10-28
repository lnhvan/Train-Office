package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.security.CurrentUser;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.Ticket;
import com.hsf302.trainoffice.entity.User;
import com.hsf302.trainoffice.service.TicketService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/tickets")
    public String list(HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        User user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", I18n.t("msg.ticket.loginRequired"));
            return "redirect:/login";
        }
        model.addAttribute("tickets", ticketService.getTicketsForUser(user));
        return "tickets/list";
    }

    @GetMapping("/tickets/{id}")
    public String detail(@PathVariable Long id,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return showTicket(id, session, model, redirectAttributes, "tickets/detail");
    }

    @GetMapping("/tickets/{id}/print")
    public String print(@PathVariable Long id,
                        HttpSession session,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        return showTicket(id, session, model, redirectAttributes, "tickets/print");
    }

    private String showTicket(Long id,
                              HttpSession session,
                              Model model,
                              RedirectAttributes redirectAttributes,
                              String viewName) {
        User user = currentUser(session);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", I18n.t("msg.ticket.loginRequired"));
            return "redirect:/login";
        }
        try {
            Ticket ticket = ticketService.getTicketDetails(id);
            if (ticket.getBooking() == null
                    || ticket.getBooking().getUser() == null
                    || !ticket.getBooking().getUser().getUserId().equals(user.getUserId())) {
                redirectAttributes.addFlashAttribute("error", I18n.t("msg.ticket.notOwner"));
                return "redirect:/tickets";
            }
            model.addAttribute("ticket", ticket);
            return viewName;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/tickets";
        }
    }

    /** Người dùng đăng nhập (JWT) của request hiện tại; null nếu chưa đăng nhập. */
    private User currentUser(HttpSession session) {
        return CurrentUser.get();
    }
}
