package com.interviewprep.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Sends booking notifications. Logging stands in for a real channel (email/SMS); no patient data is logged. */
@Component
public class BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(BookingNotifier.class);

    public void sendConfirmation(BookingConfirmedEvent event) {
        log.info(
                "Notification sent: booking {} confirmed with doctor {} for slot {}",
                event.bookingId(),
                event.doctorId(),
                event.slotStart());
    }
}
