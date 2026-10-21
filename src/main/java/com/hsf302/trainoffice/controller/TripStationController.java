package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.service.StationService;
import com.hsf302.trainoffice.service.TrainTripService;
import com.hsf302.trainoffice.service.TripStationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TripStationController {
    private final TripStationService tripStationService;
    private final TrainTripService trainTripService;
    private final StationService stationService;

    public TripStationController(TripStationService tripStationService,
                                 TrainTripService trainTripService,
                                 StationService stationService) {
        this.tripStationService = tripStationService;
        this.trainTripService = trainTripService;
        this.stationService = stationService;
    }

    @GetMapping("/admin/trips/{tripId}/stations")
    public String list(@PathVariable Long tripId, Model model, RedirectAttributes redirectAttributes) {
        return trainTripService.getTripById(tripId)
                .map(trip -> {
                    model.addAttribute("trip", trip);
                    model.addAttribute("tripStations", tripStationService.getStationsForTrip(tripId));
                    return "trip-stations/list";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Train trip not found");
                    return "redirect:/admin/trips";
                });
    }

    @GetMapping("/admin/trips/{tripId}/stations/create")
    public String create(@PathVariable Long tripId, Model model, RedirectAttributes redirectAttributes) {
        return trainTripService.getTripById(tripId)
                .map(trip -> {
                    model.addAttribute("trip", trip);
                    model.addAttribute("tripStation", new TripStation());
                    addFormOptions(model);
                    return "trip-stations/create";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Train trip not found");
                    return "redirect:/admin/trips";
                });
    }

    @PostMapping("/admin/trips/{tripId}/stations/save")
    public String save(@PathVariable Long tripId,
                       @ModelAttribute("tripStation") TripStation tripStation,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            tripStationService.saveTripStation(tripId, tripStation);
            redirectAttributes.addFlashAttribute("success", "Trip station saved");
            return "redirect:/admin/trips/" + tripId + "/stations";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            trainTripService.getTripById(tripId).ifPresent(trip -> model.addAttribute("trip", trip));
            addFormOptions(model);
            return "trip-stations/create";
        }
    }

    @GetMapping("/admin/trips/{tripId}/stations/{tripStationId}/edit")
    public String edit(@PathVariable Long tripId,
                       @PathVariable Long tripStationId,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        return tripStationService.getTripStationById(tripStationId)
                .map(tripStation -> {
                    model.addAttribute("tripStation", tripStation);
                    model.addAttribute("trip", tripStation.getTrainTrip());
                    addFormOptions(model);
                    return "trip-stations/edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Trip station not found");
                    return "redirect:/admin/trips/" + tripId + "/stations";
                });
    }

    @PostMapping("/admin/trips/{tripId}/stations/{tripStationId}/update")
    public String update(@PathVariable Long tripId,
                         @PathVariable Long tripStationId,
                         @ModelAttribute("tripStation") TripStation tripStation,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            tripStationService.updateTripStation(tripId, tripStationId, tripStation);
            redirectAttributes.addFlashAttribute("success", "Trip station updated");
            return "redirect:/admin/trips/" + tripId + "/stations";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            trainTripService.getTripById(tripId).ifPresent(trip -> model.addAttribute("trip", trip));
            addFormOptions(model);
            return "trip-stations/edit";
        }
    }

    @PostMapping("/admin/trips/{tripId}/stations/{tripStationId}/delete")
    public String deletePost(@PathVariable Long tripId,
                             @PathVariable Long tripStationId,
                             RedirectAttributes redirectAttributes) {
        return deleteTripStation(tripId, tripStationId, redirectAttributes);
    }

    @GetMapping("/admin/trips/{tripId}/stations/{tripStationId}/delete")
    public String deleteGet(@PathVariable Long tripId,
                            @PathVariable Long tripStationId,
                            RedirectAttributes redirectAttributes) {
        return deleteTripStation(tripId, tripStationId, redirectAttributes);
    }

    private String deleteTripStation(Long tripId, Long tripStationId, RedirectAttributes redirectAttributes) {
        try {
            tripStationService.deleteTripStation(tripId, tripStationId);
            redirectAttributes.addFlashAttribute("success", "Trip station deleted");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/trips/" + tripId + "/stations";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("stations", stationService.getAllStations());
    }
}
