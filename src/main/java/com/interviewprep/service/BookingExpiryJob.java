package com.interviewprep.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically marks overdue holds EXPIRED so stored statuses stay tidy. Housekeeping only: availability and new holds
 * already treat overdue holds as free. Disable with {@code app.booking.expiry-sweep.enabled=false}.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.booking.expiry-sweep",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class BookingExpiryJob {

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(
            fixedDelayString = "${app.booking.expiry-sweep.interval:1m}",
            initialDelayString = "${app.booking.expiry-sweep.interval:1m}")
    public void expireOverdueHolds() {
        bookingService.expireOverdueHolds();
    }
}
