package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.service.StationService;
import com.hsf302.trainoffice.config.AdminErrorMessageUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/stations")
public class StationController {

    private final StationService stationService;

    @Autowired
    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public String listStations(Model model,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               @Param("keyword") String keyword) {

        Page<Station> stationPage = stationService.listAll(page, keyword);

        List<Station> stations = stationPage.getContent();
        long totalItems = stationPage.getTotalElements();
        int totalPages = stationPage.getTotalPages();

        model.addAttribute("stations", stations);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);

        return "stations/admin-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("station", new Station());
        model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
        return "stations/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        Station station = stationService.getStationById(id);

        if (station != null) {
            model.addAttribute("station", station);
            model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
            return "stations/form";
        }

        return "redirect:/admin/stations";
    }

    @PostMapping("/save")
    public String saveStation(@Valid @ModelAttribute("station") Station station,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
            return "stations/form";
        }

        try {
            if (station.getStationId() == null) {
                Station createdStation = stationService.createStation(station);

                if (createdStation == null) {
                    model.addAttribute("errorMessage", "Station code already exists.");
                    model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
                    return "stations/form";
                }
            } else {
                Station updatedStation = stationService.updateStation(station.getStationId(), station);

                if (updatedStation == null) {
                    model.addAttribute("errorMessage", "Station code already exists or station was not found.");
                    model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
                    return "stations/form";
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", "Station saved successfully.");
            return "redirect:/admin/stations";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Cannot save this station. Please check the information and try again.");
            model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
            return "stations/form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteStation(@PathVariable("id") Long id,
                                RedirectAttributes redirectAttributes) {
        try {
            stationService.deleteStation(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Station with ID " + id + " has been deleted."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    AdminErrorMessageUtil.deleteMessage("station", e)
            );
        }

        return "redirect:/admin/stations";
    }
}