package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.entity.TripStation;
import com.hsf302.trainoffice.service.RouteStationService;
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
    private final RouteStationService routeStationService;

    public TripStationController(TripStationService tripStationService,
                                 TrainTripService trainTripService,
                                 RouteStationService routeStationService) {
        this.tripStationService = tripStationService;
        this.trainTripService = trainTripService;
        this.routeStationService = routeStationService;
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
                    redirectAttributes.addFlashAttribute("error", I18n.t("msg.trip.notFound"));
                    return "redirect:/admin/trips";
                });
    }

    @GetMapping("/admin/trips/{tripId}/stations/create")
    public String create(@PathVariable Long tripId, Model model, RedirectAttributes redirectAttributes) {
        return trainTripService.getTripById(tripId)
                .map(trip -> {
                    model.addAttribute("trip", trip);
                    model.addAttribute("tripStation", new TripStation());
                    addFormOptions(model, trip);
                    return "trip-stations/create";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", I18n.t("msg.trip.notFound"));
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
            redirectAttributes.addFlashAttribute("success", I18n.t("msg.tripStation.saved"));
            return "redirect:/admin/trips/" + tripId + "/stations";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            trainTripService.getTripById(tripId).ifPresent(trip -> {
                model.addAttribute("trip", trip);
                addFormOptions(model, trip);
            });
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
                    addFormOptions(model, tripStation.getTrainTrip());
                    return "trip-stations/edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", I18n.t("msg.tripStation.notFound"));
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
            redirectAttributes.addFlashAttribute("success", I18n.t("msg.tripStation.updated"));
            return "redirect:/admin/trips/" + tripId + "/stations";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            tripStation.setTripStationId(tripStationId);
            trainTripService.getTripById(tripId).ifPresent(trip -> {
                model.addAttribute("trip", trip);
                addFormOptions(model, trip);
            });
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
            redirectAttributes.addFlashAttribute("success", I18n.t("msg.tripStation.deleted"));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/trips/" + tripId + "/stations";
    }

    @PostMapping("/admin/trips/{tripId}/stations/generate")
    public String generateFromRoute(@PathVariable Long tripId, RedirectAttributes redirectAttributes) {
        try {
            int added = tripStationService.generateFromRoute(tripId);

            if (added > 0) {
                redirectAttributes.addFlashAttribute("success", I18n.t("msg.tripStation.generated", added));
            } else {
                redirectAttributes.addFlashAttribute("success", I18n.t("msg.tripStation.nothingToGenerate"));
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/trips/" + tripId + "/stations";
    }

    /** Chỉ cho chọn các ga thuộc tuyến của chuyến. */
    private void addFormOptions(Model model, TrainTrip trip) {
        model.addAttribute("routeStations",
                trip != null && trip.getRoute() != null
                        ? routeStationService.getStationsForRoute(trip.getRoute().getRouteId())
                        : java.util.List.of());
    }
}
