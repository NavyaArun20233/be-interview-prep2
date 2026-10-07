package com.interviewprep.dto.library;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Year;
import java.util.Locale;

/**
 * Request body for creating a book and for a full replacement (PUT). {@code publishedYear} is optional; when present
 * it must not be after the current year of the application clock (wired by {@code ValidationConfig}).
 */
public record BookRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 255) String author,

        @NotBlank
        @Pattern(
                regexp = "(?:\\d[- ]?){9}[\\dXx]|(?:\\d[- ]?){12}\\d",
                message = "must be an ISBN-10 or ISBN-13 (digits, optionally separated by hyphens or spaces)")
        String isbn,

        @PastOrPresent Year publishedYear) {

    /** The ISBN without separators and with an upper-case check digit, so "0-13-468599-x" equals "013468599X". */
    public String normalizedIsbn() {
        return isbn.replaceAll("[- ]", "").toUpperCase(Locale.ROOT);
    }

    /** The published year as stored, or {@code null} when omitted. */
    public Integer publishedYearValue() {
        return publishedYear == null ? null : publishedYear.getValue();
    }
}
