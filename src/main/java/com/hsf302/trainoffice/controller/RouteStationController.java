package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.RouteStation;
import com.hsf302.trainoffice.service.RouteService;
import com.hsf302.trainoffice.service.RouteStationService;
import com.hsf302.trainoffice.service.StationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class RouteStationController {

    private final RouteStationService routeStationService;
    private final RouteService routeService;
    private final StationService stationService;

    public RouteStationController(RouteStationService routeStationService,
                                  RouteService routeService,
                                  StationService stationService) {
        this.routeStationService = routeStationService;
        this.routeService = routeService;
        this.stationService = stationService;
    }

    @GetMapping("/admin/routes/{routeId}/stations")
    public String list(@PathVariable Long routeId, Model model, RedirectAttributes redirectAttributes) {
        return routeService.getRouteById(routeId)
                .map(route -> {
                    model.addAttribute("route", route);
                    model.addAttribute("routeStations", routeStationService.getStationsForRoute(routeId));
                    return "routes/stations";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Route not found");
                    return "redirect:/admin/routes";
                });
    }

    @GetMapping("/admin/routes/{routeId}/stations/new")
    public String create(@PathVariable Long routeId, Model model, RedirectAttributes redirectAttributes) {
        return routeService.getRouteById(routeId)
                .map(route -> {
                    model.addAttribute("route", route);
                    model.addAttribute("routeStation", new RouteStation());
                    model.addAttribute("formAction", "/admin/routes/" + routeId + "/stations/save");
                    addFormOptions(model);
                    return "routes/station-form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Route not found");
                    return "redirect:/admin/routes";
                });
    }

    @PostMapping("/admin/routes/{routeId}/stations/save")
    public String save(@PathVariable Long routeId,
                       @ModelAttribute("routeStation") RouteStation routeStation,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            routeStationService.saveRouteStation(routeId, routeStation);
            redirectAttributes.addFlashAttribute("successMessage", "Route station saved successfully!");
            return "redirect:/admin/routes/" + routeId + "/stations";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            routeService.getRouteById(routeId).ifPresent(route -> model.addAttribute("route", route));
            model.addAttribute("formAction", "/admin/routes/" + routeId + "/stations/save");
            addFormOptions(model);
            return "routes/station-form";
        }
    }

    @GetMapping("/admin/routes/{routeId}/stations/edit/{routeStationId}")
    public String edit(@PathVariable Long routeId,
                       @PathVariable Long routeStationId,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        return routeStationService.getRouteStationById(routeStationId)
                .filter(routeStation -> routeStation.getRoute().getRouteId().equals(routeId))
                .map(routeStation -> {
                    model.addAttribute("route", routeStation.getRoute());
                    model.addAttribute("routeStation", routeStation);
                    model.addAttribute("formAction",
                            "/admin/routes/" + routeId + "/stations/update/" + routeStationId);
                    addFormOptions(model);
                    return "routes/station-form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Route station not found");
                    return "redirect:/admin/routes/" + routeId + "/stations";
                });
    }

    @PostMapping("/admin/routes/{routeId}/stations/update/{routeStationId}")
    public String update(@PathVariable Long routeId,
                         @PathVariable Long routeStationId,
                         @ModelAttribute("routeStation") RouteStation routeStation,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            routeStationService.updateRouteStation(routeId, routeStationId, routeStation);
            redirectAttributes.addFlashAttribute("successMessage", "Route station updated successfully!");
            return "redirect:/admin/routes/" + routeId + "/stations";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            routeService.getRouteById(routeId).ifPresent(route -> model.addAttribute("route", route));
            model.addAttribute("formAction", "/admin/routes/" + routeId + "/stations/update/" + routeStationId);
            addFormOptions(model);
            return "routes/station-form";
        }
    }

    @PostMapping("/admin/routes/{routeId}/stations/reorder")
    public String reorder(@PathVariable Long routeId,
                          @RequestParam("stationIds") List<Long> stationIds,
                          RedirectAttributes redirectAttributes) {
        try {
            routeStationService.reorderStations(routeId, stationIds);
            redirectAttributes.addFlashAttribute("successMessage", "Station order updated successfully!");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/routes/" + routeId + "/stations";
    }

    @GetMapping("/admin/routes/{routeId}/stations/delete/{routeStationId}")
    public String delete(@PathVariable Long routeId,
                         @PathVariable Long routeStationId,
                         RedirectAttributes redirectAttributes) {
        try {
            routeStationService.deleteRouteStation(routeId, routeStationId);
            redirectAttributes.addFlashAttribute("successMessage", "Route station deleted successfully!");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/admin/routes/" + routeId + "/stations";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("stations", stationService.getAllStations());
    }
}