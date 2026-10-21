package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.service.SeatService;
import com.hsf302.trainoffice.service.TrainService;
import com.hsf302.trainoffice.exception.ResourceInUseException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/seats")
public class SeatController {

    private final SeatService seatService;
    private final CoachService coachService;
    private final TrainService trainService;

    public SeatController(SeatService seatService,
                          CoachService coachService,
                          TrainService trainService) {
        this.seatService = seatService;
        this.coachService = coachService;
        this.trainService = trainService;
    }

    @GetMapping
    public String listSeats(Model model,
                            @RequestParam(value = "trainId", required = false) Long trainId,
                            @RequestParam(value = "coachId", required = false) Long coachId,
                            @RequestParam(value = "keyword", required = false) String keyword,
                            @RequestParam(value = "page", defaultValue = "1") int page) {

        int safePage = Math.max(page, 1);
        String cleanKeyword = normalizeKeyword(keyword);

        List<Train> allTrains = trainService.getAllTrains();
        List<Coach> allCoaches = coachService.getAllCoaches();

        List<Coach> filteredCoaches = allCoaches;

        if (trainId != null) {
            filteredCoaches = allCoaches.stream()
                    .filter(coach -> coach.getTrain() != null
                            && coach.getTrain().getTrainId().equals(trainId))
                    .toList();

            if (coachId != null) {
                Long checkingCoachId = coachId;

                boolean coachBelongsToSelectedTrain = filteredCoaches.stream()
                        .anyMatch(coach -> coach.getCoachId().equals(checkingCoachId));

                if (!coachBelongsToSelectedTrain) {
                    coachId = null;
                }
            }
        }

        Train selectedTrain = null;

        if (trainId != null) {
            selectedTrain = allTrains.stream()
                    .filter(train -> train.getTrainId().equals(trainId))
                    .findFirst()
                    .orElse(null);
        }

        Coach selectedCoach = null;

        if (coachId != null) {
            Long finalCoachId = coachId;

            selectedCoach = filteredCoaches.stream()
                    .filter(coach -> coach.getCoachId().equals(finalCoachId))
                    .findFirst()
                    .orElse(null);
        }

        Page<Seat> seatPage = seatService.listAll(safePage, cleanKeyword, trainId, coachId);

        model.addAttribute("seats", seatPage.getContent());
        model.addAttribute("allTrains", allTrains);
        model.addAttribute("allCoaches", filteredCoaches);

        model.addAttribute("selectedTrainId", trainId);
        model.addAttribute("selectedTrain", selectedTrain);
        model.addAttribute("selectedCoachId", coachId);
        model.addAttribute("selectedCoach", selectedCoach);
        model.addAttribute("keyword", cleanKeyword);

        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", seatPage.getTotalPages());
        model.addAttribute("totalItems", seatPage.getTotalElements());

        return "seats/admin-list";
    }

    @PostMapping("/{seatId}/delete")
    public String deleteSeat(@PathVariable Long seatId,
                             @RequestParam(value = "trainId", required = false) Long trainId,
                             @RequestParam(value = "coachId", required = false) Long coachId,
                             RedirectAttributes redirectAttributes) {
        try {
            seatService.deleteSeat(seatId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Seat with ID " + seatId + " has been deleted."
            );
        } catch (ResourceInUseException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Cannot delete this seat. Please check that it exists and try again."
            );
        }

        return redirectToSeatList(trainId, coachId);
    }

    private String redirectToSeatList(Long trainId, Long coachId) {
        if (coachId != null) {
            return "redirect:/admin/seats?coachId=" + coachId
                    + (trainId == null ? "" : "&trainId=" + trainId);
        }
        if (trainId != null) {
            return "redirect:/admin/seats?trainId=" + trainId;
        }
        return "redirect:/admin/seats";
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
