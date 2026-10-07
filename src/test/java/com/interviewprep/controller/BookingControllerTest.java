package com.interviewprep.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.dto.booking.BookingResponse;
import com.interviewprep.dto.booking.HoldBookingRequest;
import com.interviewprep.entity.BookingStatus;
import com.interviewprep.exception.BookingNotFoundException;
import com.interviewprep.exception.BookingStateConflictException;
import com.interviewprep.exception.HoldExpiredException;
import com.interviewprep.exception.SlotInPastException;
import com.interviewprep.exception.SlotUnavailableException;
import com.interviewprep.service.BookingService;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    private static final LocalDateTime SLOT = LocalDateTime.parse("2026-10-08T10:00");
    private static final String BODY = """
            {"doctorId": 1, "slotStart": "2026-10-08T10:00", "patientName": "Alice"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void holdReturns201WithLocation() throws Exception {
        when(bookingService.hold(new HoldBookingRequest(1L, SLOT, "Alice"))).thenReturn(booking(BookingStatus.HELD));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/bookings/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.slotStart").value("2026-10-08T10:00:00"))
                .andExpect(jsonPath("$.slotEnd").value("2026-10-08T10:30:00"))
                .andExpect(jsonPath("$.holdExpiresAt").value("2026-10-07T04:35:00Z"));
    }

    @Test
    void holdWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\": 0, \"patientName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorId\": 1, \"slotStart\": \"tomorrow\", \"patientName\": \"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("slotStart"));
        verifyNoInteractions(bookingService);
    }

    @Test
    void holdOfATakenSlotReturns409() throws Exception {
        when(bookingService.hold(any())).thenThrow(new SlotUnavailableException(1L, SLOT));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Slot 2026-10-08T10:00 of doctor 1 is not available"));
    }

    @Test
    void holdOfAStartedSlotReturns422() throws Exception {
        when(bookingService.hold(any())).thenThrow(new SlotInPastException(SLOT));

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void getReturnsBookingOr404() throws Exception {
        when(bookingService.get(7L)).thenReturn(booking(BookingStatus.HELD));
        when(bookingService.get(8L)).thenThrow(new BookingNotFoundException(8L));

        mockMvc.perform(get("/api/v1/bookings/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
        mockMvc.perform(get("/api/v1/bookings/8"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Booking 8 not found"));
    }

    @Test
    void confirmReturns200Or410WhenExpired() throws Exception {
        when(bookingService.confirm(7L)).thenReturn(booking(BookingStatus.CONFIRMED));
        when(bookingService.confirm(8L)).thenThrow(new HoldExpiredException(8L));

        mockMvc.perform(post("/api/v1/bookings/7/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/api/v1/bookings/8/confirm")).andExpect(status().isGone());
    }

    @Test
    void cancelReturns200Or409() throws Exception {
        when(bookingService.cancel(7L)).thenReturn(booking(BookingStatus.CANCELLED));
        when(bookingService.cancel(8L))
                .thenThrow(new BookingStateConflictException(8L, BookingStatus.CANCELLED, "cancelled"));

        mockMvc.perform(post("/api/v1/bookings/7/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/bookings/8/cancel")).andExpect(status().isConflict());
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/abc/confirm")).andExpect(status().isBadRequest());
    }

    private static BookingResponse booking(BookingStatus status) {
        return new BookingResponse(
                7L,
                1L,
                SLOT,
                SLOT.plusMinutes(30),
                "Alice",
                status,
                Instant.parse("2026-10-07T04:35:00Z"),
                null,
                Instant.parse("2026-10-07T04:30:00Z"));
    }
}
