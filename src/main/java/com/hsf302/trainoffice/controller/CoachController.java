package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.service.TrainService;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/coaches")
public class CoachController {

    private static final String OTHER_COACH_TYPE = "__OTHER__";

    private final CoachService coachService;
    private final TrainService trainService;

    public CoachController(CoachService coachService, TrainService trainService) {
        this.coachService = coachService;
        this.trainService = trainService;
    }

    @GetMapping
    public String listCoaches(
            Model model,
            @RequestParam(value = "trainId", required = false) Long trainId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page
    ) {
        int safePage = Math.max(page, 1);
        String cleanKeyword = normalizeKeyword(keyword);

        List<Train> allTrains = trainService.getAllTrains();
        Train selectedTrain = null;

        if (trainId != null) {
            selectedTrain = allTrains.stream()
                    .filter(train -> train.getTrainId().equals(trainId))
                    .findFirst()
                    .orElse(null);
        }

        Page<Coach> coachPage = coachService.listAll(safePage, cleanKeyword, trainId);

        model.addAttribute("coaches", coachPage.getContent());
        model.addAttribute("allTrains", allTrains);
        model.addAttribute("selectedTrainId", trainId);
        model.addAttribute("selectedTrain", selectedTrain);
        model.addAttribute("keyword", cleanKeyword);

        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", coachPage.getTotalPages());
        model.addAttribute("totalItems", coachPage.getTotalElements());

        return "coaches/list";
    }

    private void addCommonAttributes(Model model, Long trainId) {
        if (trainId != null) {
            model.addAttribute("allTrains", trainService.getTrainById(trainId).stream().toList());
            model.addAttribute("selectedTrainId", trainId);
        } else {
            model.addAttribute("allTrains", trainService.getAllTrains());
        }

        List<String> availableCoachTypes = coachService.getAvailableCoachTypes();
        model.addAttribute("availableCoachTypes", availableCoachTypes);
        model.addAttribute("otherCoachTypeValue", OTHER_COACH_TYPE);

        if (!model.containsAttribute("selectedCoachType")) {
            Coach coach = (Coach) model.getAttribute("coach");
            String currentType = coach == null ? null : coach.getCoachType();
            String canonicalType = currentType == null ? null : availableCoachTypes.stream()
                    .filter(type -> type.equalsIgnoreCase(currentType.trim()))
                    .findFirst()
                    .orElse(null);
            model.addAttribute("selectedCoachType", canonicalType != null ? canonicalType
                    : currentType == null || currentType.isBlank() ? "" : OTHER_COACH_TYPE);
            model.addAttribute("newCoachType", canonicalType != null || currentType == null ? "" : currentType.trim());
        }
    }

    @GetMapping("/new")
    public String showCreateForm(
            Model model,
            @RequestParam(value = "trainId", required = false) Long trainId
    ) {
        Coach coach = new Coach();

        if (trainId != null) {
            trainService.getTrainById(trainId).ifPresent(coach::setTrain);
        }

        model.addAttribute("coach", coach);
        addCommonAttributes(model, trainId);

        return "coaches/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        Optional<Coach> coachOpt = coachService.getCoachById(id);

        if (coachOpt.isPresent()) {
            Coach coach = coachOpt.get();

            model.addAttribute("coach", coach);
            addCommonAttributes(model, coach.getTrain().getTrainId());

            return "coaches/form";
        }

        return "redirect:/admin/coaches";
    }

    @PostMapping("/save")
    public String saveCoach(
            @Valid @ModelAttribute("coach") Coach coach,
            BindingResult result,
            @RequestParam(value = "selectedCoachType", required = false) String selectedCoachType,
            @RequestParam(value = "newCoachType", required = false) String newCoachType,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        Long trainId = coach.getTrain() != null ? coach.getTrain().getTrainId() : null;

        String submittedType = OTHER_COACH_TYPE.equals(selectedCoachType)
                ? newCoachType
                : selectedCoachType;
        coach.setCoachType(submittedType == null ? null : submittedType.trim());

        model.addAttribute("selectedCoachType", selectedCoachType == null ? "" : selectedCoachType);
        model.addAttribute("newCoachType", newCoachType == null ? "" : newCoachType);

        if (coach.getCoachType() == null || coach.getCoachType().isBlank()) {
            result.rejectValue("coachType", "coachType.required", "Coach type is required.");
        }

        if (result.hasErrors()) {
            addCommonAttributes(model, trainId);
            return "coaches/form";
        }

        try {
            coachService.saveCoach(coach);
            redirectAttributes.addFlashAttribute("successMessage", "Coach saved successfully!");

            if (trainId != null) {
                return "redirect:/admin/coaches?trainId=" + trainId;
            }

            return "redirect:/admin/coaches";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Error saving coach: " + e.getMessage());
            addCommonAttributes(model, trainId);

            return "coaches/form";
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteCoach(
            @PathVariable("id") Long id,
            @RequestParam(value = "trainId", required = false) Long trainId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            coachService.deleteCoach(id);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Coach with ID " + id + " has been deleted."
            );
        } catch (ResourceInUseException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Error deleting coach: " + e.getMessage()
            );
        }

        if (trainId != null) {
            return "redirect:/admin/coaches?trainId=" + trainId;
        }

        return "redirect:/admin/coaches";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
