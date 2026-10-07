package com.interviewprep.controller;

import com.interviewprep.dto.booking.BookingResponse;
import com.interviewprep.dto.booking.HoldBookingRequest;
import com.interviewprep.service.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Hold, confirm and cancel appointment bookings. */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Holds a slot for the hold duration; 409 if the slot is already held or booked. */
    @PostMapping
    public ResponseEntity<BookingResponse> hold(@Valid @RequestBody HoldBookingRequest request) {
        BookingResponse booking = bookingService.hold(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(booking.id())
                .toUri();
        return ResponseEntity.created(location).body(booking);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable long id) {
        return bookingService.get(id);
    }

    /** Confirms a live hold; 410 if the hold has expired, 409 if the booking is no longer a hold. */
    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable long id) {
        return bookingService.confirm(id);
    }

    /** Cancels a confirmed booking (or a live hold), freeing the slot. */
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable long id) {
        return bookingService.cancel(id);
    }
}
