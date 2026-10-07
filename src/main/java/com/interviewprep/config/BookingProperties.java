package com.interviewprep.config;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clinic schedule ({@code app.booking}). Slots are derived from these values: {@code dayStart}, {@code dayStart +
 * slotLength}, ... up to {@code dayEnd}, in the business zone of the application {@link java.time.Clock}.
 */
@ConfigurationProperties("app.booking")
public record BookingProperties(
        Duration slotLength, Duration holdDuration, LocalTime dayStart, LocalTime dayEnd, ExpirySweep expirySweep) {

    public BookingProperties {
        Objects.requireNonNull(slotLength, "app.booking.slot-length is required");
        Objects.requireNonNull(holdDuration, "app.booking.hold-duration is required");
        Objects.requireNonNull(dayStart, "app.booking.day-start is required");
        Objects.requireNonNull(dayEnd, "app.booking.day-end is required");
        if (!slotLength.isPositive() || !holdDuration.isPositive()) {
            throw new IllegalArgumentException("app.booking slot-length and hold-duration must be positive");
        }
        if (!dayStart.isBefore(dayEnd)) {
            throw new IllegalArgumentException("app.booking.day-start must be before day-end");
        }
        if (Duration.between(dayStart, dayEnd).toNanos() % slotLength.toNanos() != 0) {
            throw new IllegalArgumentException("app.booking day window must be a whole number of slots");
        }
        if (expirySweep == null) {
            expirySweep = new ExpirySweep(true, Duration.ofMinutes(1));
        }
    }

    /** Start times of every slot of a day, in order. */
    public List<LocalTime> slotStarts() {
        List<LocalTime> starts = new ArrayList<>();
        for (LocalTime start = dayStart; start.isBefore(dayEnd); start = start.plus(slotLength)) {
            starts.add(start);
        }
        return starts;
    }

    /** Whether {@code time} is exactly the start of one of the day's slots. */
    public boolean isSlotStart(LocalTime time) {
        if (time.isBefore(dayStart) || !time.isBefore(dayEnd)) {
            return false;
        }
        return Duration.between(dayStart, time).toNanos() % slotLength.toNanos() == 0;
    }

    /** Background job that marks overdue holds EXPIRED; correctness never depends on it. */
    public record ExpirySweep(boolean enabled, Duration interval) {}
}
