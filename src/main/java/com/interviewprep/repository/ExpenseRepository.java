package com.interviewprep.repository;

import com.interviewprep.entity.Expense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    /**
     * Sums amounts per category in the database for {@code start <= expenseDate < endExclusive}. Categories without
     * expenses in the range are absent from the result.
     */
    @Query("""
            select new com.interviewprep.repository.CategoryTotal(e.category, sum(e.amount))
            from Expense e
            where e.expenseDate >= :start and e.expenseDate < :endExclusive
            group by e.category
            """)
    List<CategoryTotal> sumByCategory(@Param("start") LocalDate start, @Param("endExclusive") LocalDate endExclusive);
}
