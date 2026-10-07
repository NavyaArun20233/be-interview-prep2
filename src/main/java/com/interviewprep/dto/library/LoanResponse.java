package com.interviewprep.dto.library;

import com.interviewprep.entity.Loan;
import java.time.Instant;

/** A loan of a book; {@code returnedAt} is {@code null} while the book is still borrowed. */
public record LoanResponse(Long id, Long bookId, String memberName, Instant borrowedAt, Instant returnedAt) {

    public static LoanResponse from(Loan loan) {
        return new LoanResponse(
                loan.getId(), loan.getBook().getId(), loan.getMemberName(), loan.getBorrowedAt(), loan.getReturnedAt());
    }
}
