package com.hsf302.trainoffice.common.enums;

public enum TicketStatus {
    BOOKED("booked", "Ve da duoc dat"),
    CONFIRMED("confirmed", "Ve da xac nhan"),
    CANCELLED("cancelled", "Ve da bi huy"),
    REFUNDED("refunded", "Ve da hoan tien"),

    ACTIVE("active", "Legacy status from old seed data"),
    CHECKED_IN("checked_in", "Legacy status from old seed data"),
    EXPIRED("expired", "Legacy status from old seed data");

    private final String dbValue;
    private final String description;

    TicketStatus(String dbValue, String description) {
        this.dbValue = dbValue;
        this.description = description;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getDescription() {
        return description;
    }
}
