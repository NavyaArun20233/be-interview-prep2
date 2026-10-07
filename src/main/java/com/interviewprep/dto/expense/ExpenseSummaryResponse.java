package com.interviewprep.dto.expense;

import com.interviewprep.entity.ExpenseCategory;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Spending for one calendar month ({@code yyyy-MM}): {@code totals} has an entry for every category (0.00 when there
 * were no expenses), and {@code total} is their exact sum. Amounts serialize as JSON numbers with two decimals.
 */
public record ExpenseSummaryResponse(String month, Map<ExpenseCategory, BigDecimal> totals, BigDecimal total) {}
