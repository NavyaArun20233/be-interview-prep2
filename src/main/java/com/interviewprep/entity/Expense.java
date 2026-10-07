package com.interviewprep.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    /** Money is stored with exactly two decimal places ({@code NUMERIC(12,2)}). */
    public static final int AMOUNT_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 12, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Expense() {
        // for JPA
    }

    public Expense(BigDecimal amount, ExpenseCategory category, LocalDate expenseDate, String note, Instant now) {
        this.amount = normalize(amount);
        this.category = category;
        this.expenseDate = expenseDate;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(BigDecimal amount, ExpenseCategory category, LocalDate expenseDate, String note, Instant now) {
        this.amount = normalize(amount);
        this.category = category;
        this.expenseDate = expenseDate;
        this.note = note;
        this.updatedAt = now;
    }

    /**
     * Gives every amount the stored scale (12.5 becomes 12.50) so responses match what PostgreSQL returns. Fails
     * instead of rounding if more than two decimal places slip past validation.
     */
    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(AMOUNT_SCALE, RoundingMode.UNNECESSARY);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
