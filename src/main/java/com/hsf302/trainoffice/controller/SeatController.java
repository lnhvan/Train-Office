package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.dto.SeatMaintenanceCheckResult;
import com.hsf302.trainoffice.dto.SeatMaintenanceResult;
import com.hsf302.trainoffice.entity.Coach;
import com.hsf302.trainoffice.entity.Seat;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.CoachService;
import com.hsf302.trainoffice.service.SeatService;
import com.hsf302.trainoffice.service.TrainService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

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

    @GetMapping("/{seatId}/affected-tickets")
    @ResponseBody
    public ResponseEntity<SeatMaintenanceCheckResult> checkAffectedTickets(@PathVariable Long seatId) {
        try {
            SeatMaintenanceCheckResult result = seatService.checkFutureTickets(seatId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{seatId}/maintenance")
    public String updateMaintenance(@PathVariable Long seatId,
                                    @RequestParam("underMaintenance") boolean underMaintenance,
                                    @RequestParam(value = "confirmRefund", defaultValue = "false") boolean confirmRefund,
                                    @RequestParam(value = "trainId", required = false) Long trainId,
                                    @RequestParam(value = "coachId", required = false) Long coachId,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "page", required = false) Integer page,
                                    RedirectAttributes redirectAttributes) {
        try {
            SeatMaintenanceResult result = seatService.updateMaintenance(seatId, underMaintenance, confirmRefund);
            if (underMaintenance && result.getRefundedTicketsCount() > 0) {
                String formattedAmount = String.format("%,d", result.getRefundedAmount().longValue());
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        I18n.t("msg.seat.maintenanceOnWithRefund", result.getSeat().getSeatNumber(), result.getRefundedTicketsCount(), formattedAmount)
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "successMessage",
                        I18n.t(underMaintenance ? "msg.seat.maintenanceOn" : "msg.seat.maintenanceOff", result.getSeat().getSeatNumber())
                );
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.seat.maintenanceError"));
        }

        return redirectToSeatList(trainId, coachId, keyword, page);
    }

    private String redirectToSeatList(Long trainId, Long coachId, String keyword, Integer page) {
        UriComponentsBuilder target = UriComponentsBuilder.fromPath("/admin/seats");

        if (trainId != null) {
            target.queryParam("trainId", trainId);
        }
        if (coachId != null) {
            target.queryParam("coachId", coachId);
        }
        String cleanKeyword = normalizeKeyword(keyword);
        if (cleanKeyword != null) {
            target.queryParam("keyword", cleanKeyword);
        }
        if (page != null && page > 1) {
            target.queryParam("page", page);
        }

        return "redirect:" + target.encode().build().toUriString();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}
