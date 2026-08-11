package com.mxis.server.store.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailableTimesResponse(
        Long storeId,
        LocalDate date,
        List<TimeSlot> slots
) {
    /** time은 "HH:mm" 형식. */
    public record TimeSlot(String time, boolean available) {
    }
}
