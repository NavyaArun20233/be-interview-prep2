package com.interviewprep.repository;

import com.interviewprep.entity.Expense;
import com.interviewprep.entity.ExpenseCategory;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Criteria for listing expenses; every value is bound as a parameter, never concatenated. */
public final class ExpenseSpecifications {

    private ExpenseSpecifications() {}

    /** Matches expenses dated within [{@code from}, {@code to}] (both inclusive) and in {@code category}; null = any. */
    public static Specification<Expense> matching(LocalDate from, LocalDate to, ExpenseCategory category) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), to));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
