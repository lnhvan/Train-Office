package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.Region;
import com.hsf302.trainoffice.config.AdminErrorMessageUtil;
import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.entity.Province;
import com.hsf302.trainoffice.entity.Station;
import com.hsf302.trainoffice.service.ProvinceService;
import com.hsf302.trainoffice.service.StationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/stations")
public class StationController {

    private final StationService stationService;
    private final ProvinceService provinceService;

    @Autowired
    public StationController(StationService stationService, ProvinceService provinceService) {
        this.stationService = stationService;
        this.provinceService = provinceService;
    }

    /** Danh sách tỉnh/thành gom theo vùng cho dropdown trên form ga và bộ lọc. */
    @ModelAttribute("provincesByRegion")
    public Map<Region, List<Province>> provincesByRegion() {
        return provinceService.getProvincesByRegion();
    }

    /** Danh sách vùng miền (Bắc, Trung, Nam) cho dropdown bộ lọc. */
    @ModelAttribute("regions")
    public Region[] regions() {
        return Region.values();
    }

    @GetMapping
    public String listStations(Model model,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               @RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "region", required = false) Region region,
                               @RequestParam(value = "provinceId", required = false) Long provinceId) {

        Page<Station> stationPage = stationService.listAll(page, keyword, region, provinceId);

        List<Station> stations = stationPage.getContent();
        long totalItems = stationPage.getTotalElements();
        int totalPages = stationPage.getTotalPages();

        model.addAttribute("stations", stations);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedProvinceId", provinceId);

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
                    model.addAttribute("errorMessage", I18n.t("msg.station.codeExists"));
                    model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
                    return "stations/form";
                }
            } else {
                Station updatedStation = stationService.updateStation(station.getStationId(), station);

                if (updatedStation == null) {
                    model.addAttribute("errorMessage", I18n.t("msg.station.codeExistsOrNotFound"));
                    model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
                    return "stations/form";
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.station.saved"));
            return "redirect:/admin/stations";

        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("statusTypes", new String[]{"ACTIVE", "INACTIVE"});
            return "stations/form";
        } catch (Exception e) {
            model.addAttribute("errorMessage", I18n.t("msg.station.saveError"));
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
                    I18n.t("msg.station.deleted", id)
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
