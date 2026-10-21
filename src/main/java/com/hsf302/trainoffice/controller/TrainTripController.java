package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.service.RouteService;
import com.hsf302.trainoffice.service.TrainService;
import com.hsf302.trainoffice.service.TrainTripService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class TrainTripController {
    private final TrainTripService trainTripService;
    private final TrainService trainService;
    private final RouteService routeService;

    public TrainTripController(TrainTripService trainTripService,
                               TrainService trainService,
                               RouteService routeService) {
        this.trainTripService = trainTripService;
        this.trainService = trainService;
        this.routeService = routeService;
    }

    @GetMapping("/admin/trips")
    public String list(Model model) {
        model.addAttribute("trips", trainTripService.getAllTrips());
        return "trips/list";
    }

    @GetMapping("/admin/trips/create")
    public String create(Model model) {
        model.addAttribute("trip", new TrainTrip());
        addFormOptions(model);
        return "trips/create";
    }

    @PostMapping("/admin/trips/save")
    public String save(@ModelAttribute("trip") TrainTrip trip, RedirectAttributes redirectAttributes, Model model) {
        try {
            trainTripService.saveTrip(trip);
            redirectAttributes.addFlashAttribute("success", "Train trip created successfully");
            return "redirect:/admin/trips";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormOptions(model);
            return "trips/create";
        }
    }

    @GetMapping("/admin/trips/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return trainTripService.getTripById(id)
                .map(trip -> {
                    model.addAttribute("trip", trip);
                    return "trips/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Train trip not found");
                    return "redirect:/admin/trips";
                });
    }

    @GetMapping("/admin/trips/{id}/edit")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return trainTripService.getTripById(id)
                .map(trip -> {
                    model.addAttribute("trip", trip);
                    addFormOptions(model);
                    return "trips/edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Train trip not found");
                    return "redirect:/admin/trips";
                });
    }

    @PostMapping("/admin/trips/{id}/update")
    public String update(@PathVariable Long id,
                         @ModelAttribute("trip") TrainTrip trip,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        try {
            trainTripService.updateTrip(id, trip);
            redirectAttributes.addFlashAttribute("success", "Train trip updated successfully");
            return "redirect:/admin/trips/" + id;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormOptions(model);
            return "trips/edit";
        }
    }

    @PostMapping("/admin/trips/{id}/cancel")
    public String cancelPost(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return cancelTrip(id, redirectAttributes);
    }

    @GetMapping("/admin/trips/{id}/cancel")
    public String cancelGet(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return cancelTrip(id, redirectAttributes);
    }

    private String cancelTrip(Long id, RedirectAttributes redirectAttributes) {
        try {
            trainTripService.cancelTrip(id);
            redirectAttributes.addFlashAttribute("success", "Train trip cancelled");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/trips";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("trains", trainService.getAllTrains());
        model.addAttribute("routes", routeService.getAllRoutes());
        model.addAttribute("statuses", TripStatus.values());
    }

    @GetMapping("/booking/all-trips")
    public String allTrips(@RequestParam(value = "departureDate", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
                           Model model) {

        LocalDate selectedDate = departureDate != null ? departureDate : LocalDate.now();

        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("trips", trainTripService.getCustomerTripsByDate(selectedDate));

        return "booking/all-trips";
    }
}
