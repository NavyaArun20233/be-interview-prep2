package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Mock
    private ExpenseRepository expenseRepository;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(expenseRepository, CLOCK);
    }

    @Test
    void createStoresAmountWithTwoDecimalsAndClockTimestamps() {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExpenseResponse response =
                expenseService.create(new ExpenseRequest(new BigDecimal("12.5"), ExpenseCategory.FOOD, DAY, "Lunch"));

        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualTo(new BigDecimal("12.50"));
        assertThat(response.amount()).isEqualTo(new BigDecimal("12.50"));
        assertThat(response.category()).isEqualTo(ExpenseCategory.FOOD);
        assertThat(response.date()).isEqualTo(DAY);
        assertThat(response.note()).isEqualTo("Lunch");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void getThrowsWhenExpenseDoesNotExist() {
        when(expenseRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.get(42L))
                .isInstanceOf(ExpenseNotFoundException.class)
                .hasMessage("Expense 42 not found");
    }

    @Test
    void updateReplacesAllFieldsAndClearsOmittedNote() {
        Expense existing =
                new Expense(new BigDecimal("1.00"), ExpenseCategory.FOOD, DAY, "old", NOW.minusSeconds(3600));
        when(expenseRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(expenseRepository.saveAndFlush(existing)).thenReturn(existing);

        ExpenseResponse response = expenseService.update(
                7L, new ExpenseRequest(new BigDecimal("99.99"), ExpenseCategory.BILLS, DAY.plusDays(1), null));

        assertThat(response.amount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(response.category()).isEqualTo(ExpenseCategory.BILLS);
        assertThat(response.date()).isEqualTo(DAY.plusDays(1));
        assertThat(response.note()).isNull();
        assertThat(response.createdAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void updateOfMissingExpenseThrowsWithoutSaving() {
        when(expenseRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        expenseService.update(7L, new ExpenseRequest(BigDecimal.ONE, ExpenseCategory.OTHER, DAY, null)))
                .isInstanceOf(ExpenseNotFoundException.class);
        verify(expenseRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteOfMissingExpenseThrows() {
        when(expenseRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.delete(3L)).isInstanceOf(ExpenseNotFoundException.class);
        verify(expenseRepository, never()).delete(any(Expense.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listPagesNewestFirst() {
        when(expenseRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 0));

        PageResponse<ExpenseResponse> page =
                expenseService.list(new ExpenseFilter(DAY, DAY, ExpenseCategory.FOOD), 2, 10);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenseRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("id")));
    }

    @Test
    void summaryQueriesWholeMonthAsHalfOpenRange() {
        when(expenseRepository.sumByCategory(any(), any())).thenReturn(List.of());

        expenseService.summarize(YearMonth.of(2028, 2));

        // Leap-year February: the 29th is the last day, so the exclusive end is March 1st.
        verify(expenseRepository).sumByCategory(eq(LocalDate.of(2028, 2, 1)), eq(LocalDate.of(2028, 3, 1)));
    }

    @Test
    void summaryReturnsEveryCategoryAndAnExactTotal() {
        when(expenseRepository.sumByCategory(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(
                        new CategoryTotal(ExpenseCategory.FOOD, new BigDecimal("0.10")),
                        new CategoryTotal(ExpenseCategory.TRAVEL, new BigDecimal("0.20"))));

        ExpenseSummaryResponse summary = expenseService.summarize(YearMonth.of(2026, 10));

        assertThat(summary.month()).isEqualTo("2026-10");
        assertThat(summary.totals())
                .containsExactly(
                        entry(ExpenseCategory.FOOD, "0.10"),
                        entry(ExpenseCategory.TRAVEL, "0.20"),
                        entry(ExpenseCategory.BILLS, "0.00"),
                        entry(ExpenseCategory.OTHER, "0.00"));
        // BigDecimal.equals also compares scale: exactly 0.30, not 0.30000000000000004 or 0.3.
        assertThat(summary.total()).isEqualTo(new BigDecimal("0.30"));
    }

    @Test
    void summaryOfEmptyMonthIsAllZeroWithTwoDecimals() {
        when(expenseRepository.sumByCategory(any(), any())).thenReturn(List.of());

        ExpenseSummaryResponse summary = expenseService.summarize(YearMonth.of(2026, 1));

        assertThat(summary.totals())
                .hasSize(4)
                .allSatisfy((category, amount) -> assertThat(amount).isEqualTo(new BigDecimal("0.00")));
        assertThat(summary.total()).isEqualTo(new BigDecimal("0.00"));
    }

    private static Map.Entry<ExpenseCategory, BigDecimal> entry(ExpenseCategory category, String amount) {
        return Map.entry(category, new BigDecimal(amount));
    }
}
