package com.hsf302.trainoffice.controller;


import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.TrainStatus;
import com.hsf302.trainoffice.config.AdminErrorMessageUtil;
import com.hsf302.trainoffice.entity.Train;
import com.hsf302.trainoffice.service.TrainService;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.dto.TripCancellationResult;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.repository.TrainTripRepository;
import com.hsf302.trainoffice.service.TrainTripService;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
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
    private final TrainTripService trainTripService;
    private final TrainTripRepository trainTripRepository;

    @Autowired
    public TrainController(TrainService trainService,
                           TrainTripService trainTripService,
                           TrainTripRepository trainTripRepository) {
        this.trainService = trainService;
        this.trainTripService = trainTripService;
        this.trainTripRepository = trainTripRepository;
    }

    public TrainController(TrainService trainService) {
        this(trainService, null, null);
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

        if (trainTripService != null) {
            model.addAttribute("timelineTrips", trainTripService.getFleetTimeline(null));
        }
        model.addAttribute("allTrains", trainService.getAllTrains());

        return "trains/admin-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("train", new Train());
        addCommonAttributes(model);

        return "trains/form";
    }

    @GetMapping("/{id}")
    public String showTrainDetail(@PathVariable("id") Long id,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        Optional<Train> train = trainService.getTrainById(id);
        if (train.isPresent()) {
            model.addAttribute("detail", train.get());
            if (trainTripService != null) {
                model.addAttribute("trainTimeline", trainTripService.getTrainTimeline(id));
            }
            return "trains/detail";
        }
        redirectAttributes.addFlashAttribute("errorMessage", I18n.t("msg.train.notFound"));
        return "redirect:/admin/trains";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        Optional<Train> train = trainService.getTrainById(id);

        if (train.isPresent()) {
            model.addAttribute("train", train.get());
            if (trainTripRepository != null) {
                List<TrainTrip> scheduledTrips = trainTripRepository
                        .findByTrain_TrainIdAndStatusOrderByDepartureTimeAsc(id, TripStatus.SCHEDULED);
                model.addAttribute("scheduledTrips", scheduledTrips);
            }
            addCommonAttributes(model);

            return "trains/form";
        }

        redirectAttributes.addFlashAttribute(
                "errorMessage",
                I18n.t("msg.train.notFound")
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

        if (!result.hasFieldErrors("averageSpeedKmh") && !TrainService.isValidSpeed(train.getAverageSpeedKmh())) {
            result.rejectValue("averageSpeedKmh", "averageSpeedKmh.invalid",
                    I18n.t("err.trainSpeedInvalid", TrainService.MIN_SPEED_KMH, TrainService.MAX_SPEED_KMH));
        }

        if (result.hasErrors()) {
            if (trainTripRepository != null && train.getTrainId() != null) {
                model.addAttribute("scheduledTrips",
                        trainTripRepository.findByTrain_TrainIdAndStatusOrderByDepartureTimeAsc(train.getTrainId(), TripStatus.SCHEDULED));
            }
            addCommonAttributes(model);

            return "trains/form";
        }

        try {
            if (trainTripRepository != null && trainTripService != null && train.getTrainId() != null) {
                Optional<Train> oldTrainOpt = trainService.getTrainById(train.getTrainId());
                if (oldTrainOpt.isPresent()) {
                    Train oldTrain = oldTrainOpt.get();
                    if (oldTrain.getStatus() == TrainStatus.AVAILABLE && train.getStatus() == TrainStatus.MAINTENANCE) {
                        List<TrainTrip> scheduledTrips = trainTripRepository
                                .findByTrain_TrainIdAndStatusOrderByDepartureTimeAsc(train.getTrainId(), TripStatus.SCHEDULED);

                        if (!scheduledTrips.isEmpty()) {
                            List<String> cancelledTripDescriptions = new ArrayList<>();
                            int totalRefundedTickets = 0;
                            BigDecimal totalRefundAmount = BigDecimal.ZERO;
                            int totalCancelledHolds = 0;
                            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

                            for (TrainTrip trip : scheduledTrips) {
                                TripCancellationResult cancelResult = trainTripService.cancelTrip(trip.getTripId());
                                totalRefundedTickets += cancelResult.refundedTickets();
                                if (cancelResult.refundedAmount() != null) {
                                    totalRefundAmount = totalRefundAmount.add(cancelResult.refundedAmount());
                                }
                                totalCancelledHolds += cancelResult.cancelledHolds();

                                String routeInfo = trip.getRoute() != null
                                        ? (trip.getRoute().getRouteCode() != null ? trip.getRoute().getRouteCode() : trip.getRoute().getRouteName())
                                        : "";
                                String depInfo = trip.getDepartureTime() != null ? trip.getDepartureTime().format(dtf) : "";
                                cancelledTripDescriptions.add(String.format("Chuyến #%d (%s, %s)", trip.getTripId(), routeInfo, depInfo));
                            }

                            trainService.saveTrain(train);

                            String refundPart = "";
                            if (totalRefundedTickets > 0) {
                                refundPart += String.format(" (Đã hoàn %d vé với tổng tiền %s VNĐ)",
                                        totalRefundedTickets,
                                        NumberFormat.getInstance(new Locale("vi", "VN")).format(totalRefundAmount));
                            }
                            if (totalCancelledHolds > 0) {
                                refundPart += String.format(" (Đã hủy %d đơn giữ chỗ)", totalCancelledHolds);
                            }

                            String msg = String.format("Cập nhật tàu [%s] thành công. Tàu chuyển sang Đang bảo trì. %d chuyến tàu sau đã bị hủy tương ứng: %s.%s",
                                    train.getTrainCode(),
                                    cancelledTripDescriptions.size(),
                                    String.join("; ", cancelledTripDescriptions),
                                    refundPart);

                            redirectAttributes.addFlashAttribute("successMessage", msg);
                            return "redirect:/admin/trains";
                        } else {
                            trainService.saveTrain(train);
                            String msg = String.format("Cập nhật tàu [%s] thành công. Tàu chuyển sang Đang bảo trì (không có chuyến tàu nào bị ảnh hưởng).",
                                    train.getTrainCode());
                            redirectAttributes.addFlashAttribute("successMessage", msg);
                            return "redirect:/admin/trains";
                        }
                    }
                }
            }

            trainService.saveTrain(train);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    I18n.t("msg.train.saved")
            );

            return "redirect:/admin/trains";

        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            if (trainTripRepository != null && train.getTrainId() != null) {
                model.addAttribute("scheduledTrips",
                        trainTripRepository.findByTrain_TrainIdAndStatusOrderByDepartureTimeAsc(train.getTrainId(), TripStatus.SCHEDULED));
            }
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

    @PostMapping("/delete/{id}")
    public String deleteTrain(@PathVariable("id") Long id,
                              RedirectAttributes redirectAttributes) {
        try {
            trainService.deleteTrain(id);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    I18n.t("msg.train.deleted", id)
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
