package com.interviewprep.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.expense.ExpenseFilter;
import com.interviewprep.dto.expense.ExpenseRequest;
import com.interviewprep.dto.expense.ExpenseResponse;
import com.interviewprep.dto.expense.ExpenseSummaryResponse;
import com.interviewprep.entity.ExpenseCategory;
import com.interviewprep.exception.ExpenseNotFoundException;
import com.interviewprep.service.ExpenseService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpenseService expenseService;

    private static ExpenseResponse expense(long id, String amount, ExpenseCategory category) {
        return new ExpenseResponse(id, new BigDecimal(amount), category, DAY, null, NOW, NOW);
    }

    @Test
    void createReturns201WithLocationAndExactAmount() throws Exception {
        when(expenseService.create(any(ExpenseRequest.class))).thenReturn(expense(1L, "12.50", ExpenseCategory.FOOD));

        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 12.5, "category": "FOOD", "date": "2026-10-05", "note": "Lunch"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/expenses/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.date").value("2026-10-05"))
                .andExpect(content().string(containsString("\"amount\":12.50")));

        verify(expenseService).create(new ExpenseRequest(new BigDecimal("12.5"), ExpenseCategory.FOOD, DAY, "Lunch"));
    }

    @Test
    void createRejectsNegativeAmountAndMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -5.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'amount')].message").value("must be greater than 0"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'category')].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[?(@.field == 'date')].message").value("must not be null"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createRejectsZeroAmount() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 0, "category": "FOOD", "date": "2026-10-05"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than 0"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createRejectsMoreThanTwoDecimalPlaces() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 12.345, "category": "FOOD", "date": "2026-10-05"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("amount"))
                .andExpect(jsonPath("$.errors[0].message", startsWith("numeric value out of bounds")));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createRejectsUnknownCategoryListingAllowedValues() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1.00, "category": "RENT", "date": "2026-10-05"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of: FOOD, TRAVEL, BILLS, OTHER"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createRejectsNoteLongerThan500Characters() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1.00, "category": "OTHER", "date": "2026-10-05", "note": "%s"}
                                """.formatted("a".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("note"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void getUnknownExpenseReturns404ProblemDetail() throws Exception {
        when(expenseService.get(42L)).thenThrow(new ExpenseNotFoundException(42L));

        mockMvc.perform(get("/api/v1/expenses/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Expense 42 not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/expenses/42"));
    }

    @Test
    void listPassesFiltersAndPaging() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 31);
        when(expenseService.list(new ExpenseFilter(from, to, ExpenseCategory.TRAVEL), 1, 5))
                .thenReturn(new PageResponse<>(List.of(expense(3L, "8.00", ExpenseCategory.TRAVEL)), 1, 5, 6, 2));

        mockMvc.perform(get("/api/v1/expenses")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31")
                        .param("category", "TRAVEL")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("TRAVEL"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void listAcceptsSameDayRange() throws Exception {
        when(expenseService.list(eq(new ExpenseFilter(DAY, DAY, null)), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/expenses").param("from", "2026-10-05").param("to", "2026-10-05"))
                .andExpect(status().isOk());
    }

    @Test
    void listRejectsFromAfterTo() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").param("from", "2026-10-31").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("must be on or before to"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void listRejectsUnknownCategoryAndBadDate() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").param("category", "RENT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("category"))
                .andExpect(jsonPath("$.errors[0].message").value("must be one of: FOOD, TRAVEL, BILLS, OTHER"));

        mockMvc.perform(get("/api/v1/expenses").param("from", "01/10/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("from"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid date in the format yyyy-MM-dd"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void listRejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/expenses").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void summaryReturnsTotalsWithTwoDecimals() throws Exception {
        Map<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        totals.put(ExpenseCategory.FOOD, new BigDecimal("0.30"));
        totals.put(ExpenseCategory.TRAVEL, new BigDecimal("0.00"));
        totals.put(ExpenseCategory.BILLS, new BigDecimal("0.00"));
        totals.put(ExpenseCategory.OTHER, new BigDecimal("0.00"));
        when(expenseService.summarize(YearMonth.of(2026, 10)))
                .thenReturn(new ExpenseSummaryResponse("2026-10", totals, new BigDecimal("0.30")));

        mockMvc.perform(get("/api/v1/expenses/summary").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                                {"month": "2026-10",
                                 "totals": {"FOOD": 0.30, "TRAVEL": 0.00, "BILLS": 0.00, "OTHER": 0.00},
                                 "total": 0.30}
                                """))
                .andExpect(content().string(containsString("\"total\":0.30")));
    }

    @Test
    void summaryRejectsInvalidOrMissingMonth() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/summary").param("month", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("month"));

        mockMvc.perform(get("/api/v1/expenses/summary"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(expenseService);
    }

    @Test
    void updateReturnsUpdatedExpense() throws Exception {
        when(expenseService.update(eq(5L), any(ExpenseRequest.class)))
                .thenReturn(expense(5L, "20.00", ExpenseCategory.BILLS));

        mockMvc.perform(put("/api/v1/expenses/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "20.00", "category": "BILLS", "date": "2026-10-05"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("BILLS"));
    }

    @Test
    void updateUnknownExpenseReturns404() throws Exception {
        when(expenseService.update(eq(9L), any(ExpenseRequest.class))).thenThrow(new ExpenseNotFoundException(9L));

        mockMvc.perform(put("/api/v1/expenses/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": 1.00, "category": "BILLS", "date": "2026-10-05"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/expenses/9")).andExpect(status().isNoContent());

        verify(expenseService).delete(9L);
    }
}
