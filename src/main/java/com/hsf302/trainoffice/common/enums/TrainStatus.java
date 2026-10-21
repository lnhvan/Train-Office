package com.hsf302.trainoffice.common.enums;

public enum TrainStatus {
    AVAILABLE("Sẵn sàng"),
    ON_TRIP("Đang chạy"),
    MAINTENANCE("Đang bảo trì");

    private final String description;

    TrainStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

}
