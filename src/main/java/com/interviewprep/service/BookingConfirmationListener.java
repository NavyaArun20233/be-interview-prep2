package com.interviewprep.service;

import com.interviewprep.config.BookingConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the confirmation notification only after the confirm transaction has committed (never for a rolled-back
 * confirm), on the bounded notification executor so it does not delay the confirm response.
 */
@Component
public class BookingConfirmationListener {

    private final BookingNotifier notifier;

    public BookingConfirmationListener(BookingNotifier notifier) {
        this.notifier = notifier;
    }

    @Async(BookingConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notifier.sendConfirmation(event);
    }
}
