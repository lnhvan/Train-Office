package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.Gender;
import com.hsf302.trainoffice.entity.Passenger;
import com.hsf302.trainoffice.service.PassengerService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/passengers")
public class PassengerController {
    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @GetMapping
    public String listPassengers(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("passengers", passengerService.searchPassengers(keyword));
        model.addAttribute("keyword", keyword);
        return "passengers/list";
    }

    @GetMapping({"/new", "/create"})
    public String showCreateForm(Model model) {
        model.addAttribute("passenger", new Passenger());
        addFormOptions(model);
        return "passengers/form";
    }

    @GetMapping({"/edit/{id}", "/{id}/edit"})
    public String showEditForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return passengerService.getPassengerById(id)
                .map(passenger -> {
                    model.addAttribute("passenger", passenger);
                    addFormOptions(model);
                    return "passengers/form";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Passenger not found.");
                    return "redirect:/admin/passengers";
                });
    }

    @GetMapping("/{id}")
    public String showDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return passengerService.getPassengerById(id)
                .map(passenger -> {
                    model.addAttribute("passenger", passenger);
                    return "passengers/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Passenger not found.");
                    return "redirect:/admin/passengers";
                });
    }

    @PostMapping("/save")
    public String savePassenger(@ModelAttribute("passenger") Passenger passenger,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            passengerService.savePassenger(passenger);
            redirectAttributes.addFlashAttribute("successMessage", "Passenger saved successfully.");
            return "redirect:/admin/passengers";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("passenger", passenger);
            addFormOptions(model);
            return "passengers/form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deletePassenger(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            passengerService.deletePassenger(id);
            redirectAttributes.addFlashAttribute("successMessage", "Passenger deleted successfully.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete passenger because it is used by tickets.");
        }
        return "redirect:/admin/passengers";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("genders", Gender.values());
    }
}
