package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long BOOK_ID = 5L;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    private LoanService loanService;

    private Book book;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(bookRepository, loanRepository, CLOCK);
        book = new Book("Dune", "Frank Herbert", "9780441013593", 1965, NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(book, "id", BOOK_ID);
    }

    @Test
    void borrowAvailableBookOpensLoanAtClockTime() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(BOOK_ID)).thenReturn(false);
        when(loanRepository.saveAndFlush(any(Loan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoanResponse loan = loanService.borrow(BOOK_ID, new BorrowRequest("  Ada Lovelace "));

        assertThat(loan.bookId()).isEqualTo(BOOK_ID);
        assertThat(loan.memberName()).isEqualTo("Ada Lovelace");
        assertThat(loan.borrowedAt()).isEqualTo(NOW);
        assertThat(loan.returnedAt()).isNull();
    }

    @Test
    void borrowBookAlreadyOnLoanIsRejectedWithoutSaving() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(BOOK_ID)).thenReturn(true);

        assertThatThrownBy(() -> loanService.borrow(BOOK_ID, new BorrowRequest("Ada")))
                .isInstanceOf(BookAlreadyBorrowedException.class)
                .hasMessage("Book 5 is already borrowed and cannot be borrowed again until it is returned");
        verify(loanRepository, never()).saveAndFlush(any());
    }

    @Test
    void borrowMapsOpenLoanUniqueIndexViolationToConflict() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(BOOK_ID)).thenReturn(false);
        when(loanRepository.saveAndFlush(any(Loan.class)))
                .thenThrow(new DataIntegrityViolationException("uq_loans_active_book"));

        assertThatThrownBy(() -> loanService.borrow(BOOK_ID, new BorrowRequest("Ada")))
                .isInstanceOf(BookAlreadyBorrowedException.class);
    }

    @Test
    void borrowUnknownBookThrowsNotFound() {
        when(bookRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.borrow(99L, new BorrowRequest("Ada")))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessage("Book 99 not found");
    }

    @Test
    void returnClosesOpenLoanAtClockTime() {
        Loan open = new Loan(book, "Ada", NOW.minusSeconds(60));
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(BOOK_ID)).thenReturn(Optional.of(open));

        LoanResponse loan = loanService.returnBook(BOOK_ID);

        assertThat(loan.returnedAt()).isEqualTo(NOW);
        assertThat(open.getReturnedAt()).isEqualTo(NOW);
    }

    @Test
    void returnBookThatIsNotBorrowedIsRejected() {
        when(bookRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(book));
        when(loanRepository.findByBookIdAndReturnedAtIsNull(BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.returnBook(BOOK_ID))
                .isInstanceOf(BookNotBorrowedException.class)
                .hasMessage("Book 5 is not currently borrowed, so it cannot be returned");
    }

    @Test
    void returnUnknownBookThrowsNotFound() {
        when(bookRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.returnBook(99L)).isInstanceOf(BookNotFoundException.class);
    }
}
