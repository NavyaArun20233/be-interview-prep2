package com.interviewprep.service;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.expense.ExpenseFilter;
import com.interviewprep.dto.expense.ExpenseRequest;
import com.interviewprep.dto.expense.ExpenseResponse;
import com.interviewprep.dto.expense.ExpenseSummaryResponse;
import com.interviewprep.entity.Expense;
import com.interviewprep.entity.ExpenseCategory;
import com.interviewprep.exception.ExpenseNotFoundException;
import com.interviewprep.repository.CategoryTotal;
import com.interviewprep.repository.ExpenseRepository;
import com.interviewprep.repository.ExpenseSpecifications;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id"));
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(Expense.AMOUNT_SCALE);

    private final ExpenseRepository expenseRepository;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenseRepository, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        Expense expense = new Expense(request.amount(), request.category(), request.date(), request.note(), now());
        Expense saved = expenseRepository.save(expense);
        log.info("Created expense {}", saved.getId());
        return ExpenseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(long id) {
        return ExpenseResponse.from(findExpense(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(ExpenseFilter filter, int page, int size) {
        Page<Expense> expenses = expenseRepository.findAll(
                ExpenseSpecifications.matching(filter.from(), filter.to(), filter.category()),
                PageRequest.of(page, size, DEFAULT_SORT));
        return PageResponse.from(expenses, ExpenseResponse::from);
    }

    @Transactional
    public ExpenseResponse update(long id, ExpenseRequest request) {
        Expense expense = findExpense(id);
        expense.update(request.amount(), request.category(), request.date(), request.note(), now());
        // Flush so the optimistic-lock version is checked and incremented before the response is built.
        Expense saved = expenseRepository.saveAndFlush(expense);
        log.info("Updated expense {}", id);
        return ExpenseResponse.from(saved);
    }

    @Transactional
    public void delete(long id) {
        Expense expense = findExpense(id);
        expenseRepository.delete(expense);
        log.info("Deleted expense {}", id);
    }

    /**
     * Totals per category for {@code month}. The range is the 1st (inclusive) to the 1st of the next month
     * (exclusive), so the first and the last day are both counted whatever the month length. Sums are computed by
     * PostgreSQL on {@code NUMERIC} and added here as {@link BigDecimal}, so 0.10 + 0.20 is exactly 0.30.
     */
    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summarize(YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate endExclusive = month.plusMonths(1).atDay(1);

        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) {
            totals.put(category, ZERO);
        }
        for (CategoryTotal row : expenseRepository.sumByCategory(start, endExclusive)) {
            totals.put(row.category(), row.total().setScale(Expense.AMOUNT_SCALE, RoundingMode.UNNECESSARY));
        }
        BigDecimal total = totals.values().stream().reduce(ZERO, BigDecimal::add);
        return new ExpenseSummaryResponse(month.toString(), Collections.unmodifiableMap(totals), total);
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Expense findExpense(long id) {
        return expenseRepository.findById(id).orElseThrow(() -> new ExpenseNotFoundException(id));
    }
}
