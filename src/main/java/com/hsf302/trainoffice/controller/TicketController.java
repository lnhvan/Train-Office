package com.hsf302.trainoffice.controller;

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
            redirectAttributes.addFlashAttribute("error", "Please log in to view tickets");
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
            redirectAttributes.addFlashAttribute("error", "Please log in to view tickets");
            return "redirect:/login";
        }
        try {
            Ticket ticket = ticketService.getTicketDetails(id);
            if (ticket.getBooking() == null
                    || ticket.getBooking().getUser() == null
                    || !ticket.getBooking().getUser().getUserId().equals(user.getUserId())) {
                redirectAttributes.addFlashAttribute("error", "Ticket does not belong to current user");
                return "redirect:/tickets";
            }
            model.addAttribute("ticket", ticket);
            return viewName;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/tickets";
        }
    }

    private User currentUser(HttpSession session) {
        Object sessionUser = session.getAttribute("currentUser");
        return sessionUser instanceof User user ? user : null;
    }
}
