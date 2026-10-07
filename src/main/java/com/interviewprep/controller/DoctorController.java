package com.interviewprep.controller;

import com.interviewprep.dto.booking.AvailableSlotsResponse;
import com.interviewprep.service.BookingService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** A doctor's bookable slots. */
@RestController
@RequestMapping("/api/v1/doctors")
public class DoctorController {

    private final BookingService bookingService;

    public DoctorController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Free slots of the doctor on {@code date} (clinic-local), excluding slots that have already started. */
    @GetMapping("/{id}/slots")
    public AvailableSlotsResponse availableSlots(
            @PathVariable long id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.availableSlots(id, date);
    }
}
