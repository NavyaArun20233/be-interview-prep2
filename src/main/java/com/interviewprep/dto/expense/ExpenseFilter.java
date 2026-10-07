package com.interviewprep.dto.expense;

import com.interviewprep.entity.ExpenseCategory;
import com.interviewprep.exception.FieldValidationException;
import java.time.LocalDate;

/**
 * Optional list filters: an inclusive date range and a category; {@code null} means "no restriction". Construction
 * rejects {@code from} after {@code to} (400), so every filter instance describes a valid range.
 */
public record ExpenseFilter(LocalDate from, LocalDate to, ExpenseCategory category) {

    public ExpenseFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FieldValidationException("from", "must be on or before to");
        }
    }
}
