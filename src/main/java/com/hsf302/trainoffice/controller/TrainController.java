package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.config.AdminErrorMessageUtil;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.TrainService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;
import java.util.List;

@Controller
@RequestMapping("/admin/trains")
public class TrainController {

    private static final String OTHER_TRAIN_TYPE = "__OTHER__";

    private final TrainService trainService;

    @Autowired
    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    private void addCommonAttributes(Model model) {
        model.addAttribute("allTrainStatus", TrainStatus.values());
        List<String> availableTrainTypes = trainService.getAvailableTrainTypes();
        model.addAttribute("availableTrainTypes", availableTrainTypes);
        model.addAttribute("otherTrainTypeValue", OTHER_TRAIN_TYPE);

        if (!model.containsAttribute("selectedTrainType")) {
            Train train = (Train) model.getAttribute("train");
            String currentType = train == null ? null : train.getTrainType();
            String canonicalType = currentType == null ? null : availableTrainTypes.stream()
                    .filter(type -> type.equalsIgnoreCase(currentType.trim()))
                    .findFirst()
                    .orElse(null);
            model.addAttribute("selectedTrainType", canonicalType != null ? canonicalType
                    : currentType == null || currentType.isBlank() ? "" : OTHER_TRAIN_TYPE);
            model.addAttribute("newTrainType", canonicalType != null || currentType == null ? "" : currentType.trim());
        }
    }

    @GetMapping
    public String listTrains(
            Model model,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        int safePage = Math.max(page, 1);
        String cleanKeyword = normalizeKeyword(keyword);

        Page<Train> trainPage = trainService.listAll(safePage, cleanKeyword);

        model.addAttribute("trains", trainPage.getContent());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", trainPage.getTotalPages());
        model.addAttribute("totalItems", trainPage.getTotalElements());
        model.addAttribute("keyword", cleanKeyword);

        return "trains/admin-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("train", new Train());
        addCommonAttributes(model);

        return "trains/form";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        Optional<Train> train = trainService.getTrainById(id);

        if (train.isPresent()) {
            model.addAttribute("train", train.get());
            addCommonAttributes(model);

            return "trains/form";
        }

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Train was not found."
        );

        return "redirect:/admin/trains";
    }

    @PostMapping("/save")
    public String saveTrain(@Valid @ModelAttribute("train") Train train,
                            BindingResult result,
                            @RequestParam(value = "selectedTrainType", required = false) String selectedTrainType,
                            @RequestParam(value = "newTrainType", required = false) String newTrainType,
                            Model model,
                            RedirectAttributes redirectAttributes) {

        String submittedType = OTHER_TRAIN_TYPE.equals(selectedTrainType)
                ? newTrainType
                : selectedTrainType;
        train.setTrainType(submittedType == null ? null : submittedType.trim());

        model.addAttribute("selectedTrainType", selectedTrainType == null ? "" : selectedTrainType);
        model.addAttribute("newTrainType", newTrainType == null ? "" : newTrainType);

        if (train.getTrainType() == null || train.getTrainType().isBlank()) {
            result.rejectValue("trainType", "trainType.required", "Train type is required.");
        }

        if (result.hasErrors()) {
            addCommonAttributes(model);

            return "trains/form";
        }

        try {
            trainService.saveTrain(train);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Train saved successfully."
            );

            return "redirect:/admin/trains";

        } catch (IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            addCommonAttributes(model);

            return "trains/form";

        } catch (Exception e) {
            model.addAttribute(
                    "errorMessage",
                    "Cannot save this train. Please check the information and try again."
            );
            addCommonAttributes(model);

            return "trains/form";
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteTrain(@PathVariable("id") Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            trainService.deleteTrain(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Train with ID " + id + " has been deleted."
            );

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    AdminErrorMessageUtil.deleteMessage("train", e)
            );
        }

        return "redirect:/admin/trains";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
