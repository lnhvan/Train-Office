package com.hsf302.trainoffice.controller;

import com.hsf302.trainoffice.dto.TripCancellationResult;
import java.math.RoundingMode;

import com.hsf302.trainoffice.config.I18n;
import com.hsf302.trainoffice.common.enums.TripStatus;
import com.hsf302.trainoffice.entity.TrainTrip;
import com.hsf302.trainoffice.service.RouteService;
import com.hsf302.trainoffice.service.TrainService;
import com.hsf302.trainoffice.service.TrainTripService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Controller
public class TrainTripController {
    private final TrainTripService trainTripService;
    private final TrainService trainService;
    private final RouteService routeService;

    public TrainTripController(TrainTripService trainTripService,
                               TrainService trainService,
                               RouteService routeService) {
        this.trainTripService = trainTripService;
        this.trainService = trainService;
        this.routeService = routeService;
    }

    @GetMapping("/admin/trips")
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "trainId", required = false) Long trainId,
                       @RequestParam(value = "routeId", required = false) Long routeId,
                       @RequestParam(value = "status", required = false) TripStatus status,
                       @RequestParam(value = "departureDate", required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
                       Model model) {
        List<TrainTrip> trips = trainTripService.filterTrips(keyword, trainId, routeId, status, departureDate);
        model.addAttribute("trips", trips);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedTrainId", trainId);
        model.addAttribute("selectedRouteId", routeId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDepartureDate", departureDate);
        addFormOptions(model);
        return "trips/list";
    }

    @GetMapping("/admin/trips/create")
    public String create(Model model) {
        TrainTrip newTrip = new TrainTrip();
        newTrip.setStatus(TripStatus.SCHEDULED);
        model.addAttribute("trip", newTrip);
        addFormOptions(model);
        return "trips/create";
    }

    /**
     * Xem trước lịch dừng cho form tạo chuyến (JSON). Không lưu gì.
     * departureTime theo định dạng của input datetime-local, ví dụ 2026-11-09T14:39.
     */
    @GetMapping("/admin/trips/schedule-preview")
    @ResponseBody
    public ResponseEntity<?> schedulePreview(@RequestParam(value = "trainId", required = false) Long trainId,
                                             @RequestParam(value = "routeId", required = false) Long routeId,
                                             @RequestParam(value = "departureTime", required = false) String departureTime) {
        try {
            LocalDateTime departure;
            try {
                departure = departureTime == null || departureTime.isBlank() ? null : LocalDateTime.parse(departureTime);
            } catch (DateTimeParseException ex) {
                departure = null;
            }

            return ResponseEntity.ok(trainTripService.previewSchedule(trainId, routeId, departure));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/admin/trips/save")
    public String save(@ModelAttribute("trip") TrainTrip trip, RedirectAttributes redirectAttributes, Model model) {
        try {
            trainTripService.saveTrip(trip);
            redirectAttributes.addFlashAttribute("success", I18n.t("msg.trip.created"));
            return "redirect:/admin/trips";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            addFormOptions(model);
            return "trips/create";
        }
    }

    /**
     * Chi tiết chuyến. Thẻ "Tóm tắt lịch chạy" có sẵn form sửa ẩn bên trong:
     * bấm Sửa thì JS mở form tại chỗ; ?edit=true mở sẵn (dùng khi không có JS).
     */
    @GetMapping("/admin/trips/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(value = "edit", defaultValue = "false") boolean edit,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        return trainTripService.getTripById(id)
                .map(trip -> {
                    model.addAttribute("trip", trip);
                    model.addAttribute("tripForm", trip);
                    model.addAttribute("editing", edit);
                    addFormOptions(model);
                    return "trips/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", I18n.t("msg.trip.notFound"));
                    return "redirect:/admin/trips";
                });
    }

    /** Trang sửa riêng không còn dùng: chuyển về trang chi tiết với form sửa mở sẵn. */
    @GetMapping("/admin/trips/{id}/edit")
    public String edit(@PathVariable Long id) {
        return "redirect:/admin/trips/" + id + "?edit=true";
    }

    @PostMapping("/admin/trips/{id}/update")
    public String update(@PathVariable Long id,
                         @ModelAttribute("tripForm") TrainTrip tripForm,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        try {
            trainTripService.updateTrip(id, tripForm);
            redirectAttributes.addFlashAttribute("successMessage", I18n.t("msg.trip.updated"));
            return "redirect:/admin/trips/" + id;
        } catch (IllegalArgumentException ex) {
            // Lưu lỗi: hiện lại trang chi tiết (dữ liệu trong DB) với form đang mở, giữ giá trị vừa nhập.
            return trainTripService.getTripById(id)
                    .map(trip -> {
                        model.addAttribute("trip", trip);
                        model.addAttribute("editing", true);
                        model.addAttribute("error", ex.getMessage());
                        addFormOptions(model);
                        return "trips/detail";
                    })
                    .orElse("redirect:/admin/trips");
        }
    }

    @PostMapping("/admin/trips/{id}/cancel")
    public String cancelPost(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return cancelTrip(id, redirectAttributes);
    }

    private String cancelTrip(Long id, RedirectAttributes redirectAttributes) {
        try {
            TripCancellationResult result = trainTripService.cancelTrip(id);
            String message = result.refundedTickets() == 0 && result.cancelledHolds() == 0
                    ? I18n.t("msg.trip.cancelled")
                    : I18n.t("msg.trip.cancelledWithRefunds",
                            result.refundedTickets(),
                            String.format("%,d", result.refundedAmount().setScale(0, RoundingMode.HALF_UP).longValue()),
                            result.cancelledHolds());
            redirectAttributes.addFlashAttribute("success", message);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Ví không đủ tiền hoàn: cả thao tác hủy được rollback.
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/trips";
    }

    private void addFormOptions(Model model) {
        model.addAttribute("trains", trainService.getAllTrains());
        model.addAttribute("routes", routeService.getAllRoutes());
        model.addAttribute("statuses", TripStatus.values());
    }

    @GetMapping("/booking/all-trips")
    public String allTrips(@RequestParam(value = "departureDate", required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate departureDate,
                           Model model) {

        LocalDate selectedDate = departureDate != null ? departureDate : LocalDate.now();

        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("trips", trainTripService.getCustomerTripsByDate(selectedDate));

        return "booking/all-trips";
    }
}
