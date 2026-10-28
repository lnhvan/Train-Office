package com.hsf302.trainoffice.config;

import org.springframework.dao.DataIntegrityViolationException;

public final class AdminErrorMessageUtil {

    private AdminErrorMessageUtil() {
    }

    public static String deleteMessage(String entityName, Exception exception) {
        String message = getFullMessage(exception).toLowerCase();

        if (isForeignKeyError(exception, message)) {
            return buildForeignKeyDeleteMessage(entityName, message);
        }

        return I18n.t("msg.delete.generic", localizedEntityName(entityName));
    }

    private static String buildForeignKeyDeleteMessage(String entityName, String message) {
        String normalizedEntity = normalizeEntityName(entityName);

        if (message.contains("route_stations") || message.contains("route_station")) {
            if (message.contains("route_id") || message.contains("delete from routes")) {
                return I18n.t("msg.delete.routeHasStations");
            }

            if (message.contains("station_id") || message.contains("delete from stations")) {
                return I18n.t("msg.delete.stationInRoutes");
            }
        }

        if (message.contains("coaches") || message.contains("coach")) {
            if (message.contains("train_id") || message.contains("delete from trains")) {
                return I18n.t("msg.delete.trainHasCoaches");
            }
        }

        if (message.contains("seats") || message.contains("seat")) {
            if (message.contains("coach_id") || message.contains("delete from coaches")) {
                return I18n.t("msg.delete.coachHasSeats");
            }
        }

        if (message.contains("tickets") || message.contains("ticket")) {
            if (message.contains("seat_id") || message.contains("delete from seats")) {
                return I18n.t("msg.delete.seatInTickets");
            }

            if (message.contains("booking_id") || message.contains("delete from bookings")) {
                return I18n.t("msg.delete.bookingHasTickets");
            }
        }

        if (message.contains("payments") || message.contains("payment")) {
            if (message.contains("booking_id") || message.contains("delete from bookings")) {
                return I18n.t("msg.delete.bookingHasPayments");
            }
        }

        if (message.contains("train_trips") || message.contains("train_trip") || message.contains("trips")) {
            if (message.contains("train_id") || message.contains("delete from trains")) {
                return I18n.t("msg.delete.trainInTrips");
            }

            if (message.contains("route_id") || message.contains("delete from routes")) {
                return I18n.t("msg.delete.routeInTrips");
            }
        }

        if (message.contains("passengers") || message.contains("passenger")) {
            return I18n.t("msg.delete.linkedPassengers");
        }

        if (message.contains("refunds") || message.contains("refund")) {
            return I18n.t("msg.delete.linkedRefunds");
        }

        if (message.contains("invoices") || message.contains("invoice")) {
            return I18n.t("msg.delete.linkedInvoices");
        }

        return I18n.t("msg.delete.inUse", localizedEntityName(entityName));
    }

    private static boolean isForeignKeyError(Exception exception, String message) {
        return exception instanceof DataIntegrityViolationException
                || message.contains("reference constraint")
                || message.contains("foreign key")
                || message.contains("constraint")
                || message.contains("conflicted with the reference constraint");
    }

    private static String getFullMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();

        Throwable current = throwable;

        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(" ");
            }

            current = current.getCause();
        }

        return builder.toString();
    }

    /** Tên đối tượng theo ngôn ngữ hiện tại (key entity.route, entity.station...). */
    private static String localizedEntityName(String entityName) {
        String normalized = normalizeEntityName(entityName);
        String localized = I18n.t("entity." + normalized);
        return localized.startsWith("entity.") ? normalized : localized;
    }

    private static String normalizeEntityName(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return "item";
        }

        return entityName.trim().toLowerCase();
    }
}