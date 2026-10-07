package com.interviewprep.dto.expense;

import com.interviewprep.entity.ExpenseCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for creating an expense and for replacing one (PUT); an omitted {@code note} is cleared on update.
 *
 * <p>{@code amount} is a {@link BigDecimal} so it is never rounded through binary floating point; the limits match
 * the {@code NUMERIC(12,2)} column.
 */
public record ExpenseRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2)
        BigDecimal amount,

        @NotNull ExpenseCategory category,
        @NotNull LocalDate date,
        @Size(max = 500) String note) {}
