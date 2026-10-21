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

        return "Cannot delete this " + normalizeEntityName(entityName)
                + ". Please check whether it is still being used by other data.";
    }

    private static String buildForeignKeyDeleteMessage(String entityName, String message) {
        String normalizedEntity = normalizeEntityName(entityName);

        if (message.contains("route_stations") || message.contains("route_station")) {
            if (message.contains("route_id") || message.contains("delete from routes")) {
                return "Cannot delete this route because it still has route stations. Please remove related route stations first.";
            }

            if (message.contains("station_id") || message.contains("delete from stations")) {
                return "Cannot delete this station because it is still used in routes. Please remove related route stations first.";
            }
        }

        if (message.contains("coaches") || message.contains("coach")) {
            if (message.contains("train_id") || message.contains("delete from trains")) {
                return "Cannot delete this train because it still has coaches. Please delete related coaches first.";
            }
        }

        if (message.contains("seats") || message.contains("seat")) {
            if (message.contains("coach_id") || message.contains("delete from coaches")) {
                return "Cannot delete this coach because it still has seats. Please delete related seats first.";
            }
        }

        if (message.contains("tickets") || message.contains("ticket")) {
            if (message.contains("seat_id") || message.contains("delete from seats")) {
                return "Cannot delete this seat because it has already been used in tickets.";
            }

            if (message.contains("booking_id") || message.contains("delete from bookings")) {
                return "Cannot delete this booking because it already has tickets.";
            }
        }

        if (message.contains("payments") || message.contains("payment")) {
            if (message.contains("booking_id") || message.contains("delete from bookings")) {
                return "Cannot delete this booking because it already has payment records.";
            }
        }

        if (message.contains("train_trips") || message.contains("train_trip") || message.contains("trips")) {
            if (message.contains("train_id") || message.contains("delete from trains")) {
                return "Cannot delete this train because it is still used in train trips.";
            }

            if (message.contains("route_id") || message.contains("delete from routes")) {
                return "Cannot delete this route because it is still used in train trips.";
            }
        }

        if (message.contains("passengers") || message.contains("passenger")) {
            return "Cannot delete this data because passenger records are still linked to it.";
        }

        if (message.contains("refunds") || message.contains("refund")) {
            return "Cannot delete this data because refund records are still linked to it.";
        }

        if (message.contains("invoices") || message.contains("invoice")) {
            return "Cannot delete this data because invoice records are still linked to it.";
        }

        return "Cannot delete this " + normalizedEntity
                + " because it is still being used by other records. Please delete or update related data first.";
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

    private static String normalizeEntityName(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return "item";
        }

        return entityName.trim().toLowerCase();
    }
}