package com.interviewprep.controller;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.expense.ExpenseFilter;
import com.interviewprep.dto.expense.ExpenseRequest;
import com.interviewprep.dto.expense.ExpenseResponse;
import com.interviewprep.dto.expense.ExpenseSummaryResponse;
import com.interviewprep.entity.ExpenseCategory;
import com.interviewprep.service.ExpenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    static final int MAX_PAGE_SIZE = 100;

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@Valid @RequestBody ExpenseRequest request) {
        ExpenseResponse created = expenseService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Lists expenses newest first; {@code from} and {@code to} are inclusive ISO dates ({@code yyyy-MM-dd}). */
    @GetMapping
    public PageResponse<ExpenseResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return expenseService.list(new ExpenseFilter(from, to, category), page, size);
    }

    /** Totals per category and overall for the calendar month {@code month} ({@code yyyy-MM}). */
    @GetMapping("/summary")
    public ExpenseSummaryResponse summary(@RequestParam YearMonth month) {
        return expenseService.summarize(month);
    }

    @GetMapping("/{id}")
    public ExpenseResponse get(@PathVariable long id) {
        return expenseService.get(id);
    }

    @PutMapping("/{id}")
    public ExpenseResponse update(@PathVariable long id, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        expenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
