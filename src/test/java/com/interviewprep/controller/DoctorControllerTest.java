package com.interviewprep.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.dto.booking.AvailableSlotsResponse;
import com.interviewprep.dto.booking.SlotResponse;
import com.interviewprep.exception.DoctorNotFoundException;
import com.interviewprep.service.BookingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorController.class)
class DoctorControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @Test
    void listsAvailableSlots() throws Exception {
        LocalDateTime start = DATE.atTime(9, 0);
        when(bookingService.availableSlots(1L, DATE))
                .thenReturn(
                        new AvailableSlotsResponse(1L, DATE, List.of(new SlotResponse(start, start.plusMinutes(30)))));

        mockMvc.perform(get("/api/v1/doctors/1/slots").param("date", "2026-10-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(1))
                .andExpect(jsonPath("$.date").value("2026-10-08"))
                .andExpect(jsonPath("$.slots[0].start").value("2026-10-08T09:00:00"))
                .andExpect(jsonPath("$.slots[0].end").value("2026-10-08T09:30:00"));
    }

    @Test
    void missingOrMalformedDateReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/doctors/1/slots")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/doctors/1/slots").param("date", "08/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("date"));
        verifyNoInteractions(bookingService);
    }

    @Test
    void unknownDoctorReturns404() throws Exception {
        when(bookingService.availableSlots(9L, DATE)).thenThrow(new DoctorNotFoundException(9L));

        mockMvc.perform(get("/api/v1/doctors/9/slots").param("date", "2026-10-08"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Doctor 9 not found"));
    }
}
