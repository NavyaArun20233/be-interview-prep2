package com.interviewprep.service;

import com.interviewprep.dto.library.BorrowRequest;
import com.interviewprep.dto.library.LoanResponse;
import com.interviewprep.entity.Book;
import com.interviewprep.entity.Loan;
import com.interviewprep.exception.BookAlreadyBorrowedException;
import com.interviewprep.exception.BookNotBorrowedException;
import com.interviewprep.exception.BookNotFoundException;
import com.interviewprep.repository.BookRepository;
import com.interviewprep.repository.LoanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrowing and returning books. Every operation locks the book row first, so borrow, return and delete of one book
 * run one at a time; the partial unique index {@code uq_loans_active_book} is the database backstop for "at most one
 * open loan per book".
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public LoanService(BookRepository bookRepository, LoanRepository loanRepository, Clock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    @Transactional
    public LoanResponse borrow(long bookId, BorrowRequest request) {
        Book book = lockBook(bookId);
        if (loanRepository.existsByBookIdAndReturnedAtIsNull(bookId)) {
            throw new BookAlreadyBorrowedException(bookId);
        }
        Loan loan = new Loan(book, request.memberName().strip(), now());
        Loan saved;
        try {
            saved = loanRepository.saveAndFlush(loan);
        } catch (DataIntegrityViolationException ex) {
            // The book exists and is locked, so the only constraint this insert can break is the open-loan index.
            throw new BookAlreadyBorrowedException(bookId);
        }
        // Member names are personal data: log ids only.
        log.info("Book {} borrowed as loan {}", bookId, saved.getId());
        return LoanResponse.from(saved);
    }

    @Transactional
    public LoanResponse returnBook(long bookId) {
        lockBook(bookId);
        Loan loan = loanRepository
                .findByBookIdAndReturnedAtIsNull(bookId)
                .orElseThrow(() -> new BookNotBorrowedException(bookId));
        loan.markReturned(now());
        log.info("Book {} returned, loan {} closed", bookId, loan.getId());
        return LoanResponse.from(loan);
    }

    private Book lockBook(long bookId) {
        return bookRepository.findByIdForUpdate(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
