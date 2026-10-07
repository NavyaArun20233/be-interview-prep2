package com.interviewprep.dto.expense;

import com.interviewprep.entity.Expense;
import com.interviewprep.entity.ExpenseCategory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id,
        BigDecimal amount,
        ExpenseCategory category,
        LocalDate date,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount(),
                expense.getCategory(),
                expense.getExpenseDate(),
                expense.getNote(),
                expense.getCreatedAt(),
                expense.getUpdatedAt());
    }
}
