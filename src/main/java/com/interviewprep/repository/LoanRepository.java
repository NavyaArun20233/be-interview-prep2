package com.interviewprep.repository;

import com.interviewprep.entity.Loan;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    boolean existsByBookIdAndReturnedAtIsNull(long bookId);

    Optional<Loan> findByBookIdAndReturnedAtIsNull(long bookId);

    /** Which of the given books are on loan right now, in one query (avoids N+1 when listing books). */
    @Query("select l.book.id from Loan l where l.book.id in :bookIds and l.returnedAt is null")
    Set<Long> findBookIdsOnLoan(@Param("bookIds") Collection<Long> bookIds);
}
