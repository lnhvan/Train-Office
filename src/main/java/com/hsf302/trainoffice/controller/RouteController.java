package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.config.AdminErrorMessageUtil;
import com.hsf302.trainoffice.entity.Route;
import com.hsf302.trainoffice.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public String listRoutes(
            Model model,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        int safePage = Math.max(page, 1);
        String cleanKeyword = normalizeKeyword(keyword);

        Page<Route> routePage = routeService.listAll(safePage, cleanKeyword);

        model.addAttribute("routes", routePage.getContent());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", routePage.getTotalPages());
        model.addAttribute("totalItems", routePage.getTotalElements());
        model.addAttribute("keyword", cleanKeyword);

        return "routes/admin-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("route", new Route());
        return "routes/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        return routeService.getRouteById(id)
                .map(route -> {
                    model.addAttribute("route", route);
                    return "routes/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(
                            "errorMessage",
                            "Route was not found."
                    );
                    return "redirect:/admin/routes";
                });
    }

    @PostMapping("/save")
    public String saveRoute(@Valid @ModelAttribute("route") Route route,
                            BindingResult result,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            return "routes/form";
        }

        try {
            routeService.saveRoute(route);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Route saved successfully."
            );

            return "redirect:/admin/routes";

        } catch (Exception e) {
            model.addAttribute(
                    "errorMessage",
                    "Cannot save this route. Please check the information and try again."
            );

            return "routes/form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteRoute(@PathVariable Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            routeService.deleteRoute(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Route deleted successfully."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    AdminErrorMessageUtil.deleteMessage("route", e)
            );
        }

        return "redirect:/admin/routes";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}