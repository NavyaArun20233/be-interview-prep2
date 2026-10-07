package com.interviewprep.repository;

import com.interviewprep.entity.ExpenseCategory;
import java.math.BigDecimal;

/** One row of the monthly summary aggregate: the exact sum of the amounts in a category. */
public record CategoryTotal(ExpenseCategory category, BigDecimal total) {}
