package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.library.BookRequest;
import com.interviewprep.dto.library.BookResponse;
import com.interviewprep.entity.Book;
import com.interviewprep.exception.BookNotFoundException;
import com.interviewprep.exception.BookOnLoanException;
import com.interviewprep.exception.DuplicateIsbnException;
import com.interviewprep.repository.BookRepository;
import com.interviewprep.repository.LoanRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanRepository loanRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(bookRepository, loanRepository, CLOCK);
    }

    private static BookRequest request(String isbn) {
        return new BookRequest("Clean Code", "Robert C. Martin", isbn, Year.of(2008));
    }

    private static Book book(long id, String title, String isbn) {
        Book book = new Book(title, "Author", isbn, 2008, NOW);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new ConstraintViolationException("duplicate key", new SQLException("23505"), constraint));
    }

    @Test
    void createNormalizesIsbnAndSetsTimestampsFromClock() {
        when(bookRepository.existsByIsbn("013235088X")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookResponse response = bookService.create(request("0-13-235088-x"));

        ArgumentCaptor<Book> saved = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIsbn()).isEqualTo("013235088X");
        assertThat(response.isbn()).isEqualTo("013235088X");
        assertThat(response.publishedYear()).isEqualTo(2008);
        assertThat(response.available()).isTrue();
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsExistingIsbnWithoutSaving() {
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(true);

        assertThatThrownBy(() -> bookService.create(request("978-0-13-235088-4")))
                .isInstanceOf(DuplicateIsbnException.class)
                .hasMessage("A book with ISBN 9780132350884 already exists");
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void createMapsLostIsbnRaceOnUniqueConstraintToConflict() {
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenThrow(violation("uq_books_isbn"));

        assertThatThrownBy(() -> bookService.create(request("9780132350884")))
                .isInstanceOf(DuplicateIsbnException.class);
    }

    @Test
    void createRethrowsOtherConstraintViolations() {
        DataIntegrityViolationException other = violation("some_other_constraint");
        when(bookRepository.existsByIsbn("9780132350884")).thenReturn(false);
        when(bookRepository.saveAndFlush(any(Book.class))).thenThrow(other);

        assertThatThrownBy(() -> bookService.create(request("9780132350884"))).isSameAs(other);
    }

    @Test
    void getThrowsWhenBookDoesNotExist() {
        when(bookRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.get(42L))
                .isInstanceOf(BookNotFoundException.class)
                .hasMessage("Book 42 not found");
    }

    @Test
    void getReportsBookOnLoanAsUnavailable() {
        when(bookRepository.findById(7L)).thenReturn(Optional.of(book(7L, "Dune", "9780441013593")));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(7L)).thenReturn(true);

        assertThat(bookService.get(7L).available()).isFalse();
    }

    @Test
    void listWithBlankQueryListsAllAndResolvesAvailabilityInOneQuery() {
        Pageable pageable = PageRequest.of(0, 20);
        List<Book> books = List.of(book(1L, "Dune", "9780441013593"), book(2L, "Emma", "9780141439587"));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(books, pageable, 2));
        when(loanRepository.findBookIdsOnLoan(List.of(1L, 2L))).thenReturn(Set.of(2L));

        PageResponse<BookResponse> page = bookService.list(Optional.of("   "), 0, 20);

        assertThat(page.content()).extracting(BookResponse::available).containsExactly(true, false);
        assertThat(page.totalElements()).isEqualTo(2);
        verify(bookRepository, never())
                .findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(any(), any(), any());
    }

    @Test
    void listWithQuerySearchesTitleOrAuthorWithStrippedTerm() {
        when(bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(
                        eq("dune"), eq("dune"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<BookResponse> page = bookService.list(Optional.of(" dune "), 0, 20);

        assertThat(page.content()).isEmpty();
        verify(loanRepository, never()).findBookIdsOnLoan(anyCollection());
    }

    @Test
    void updateRejectsIsbnOfAnotherBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book(1L, "Dune", "9780441013593")));
        when(bookRepository.existsByIsbnAndIdNot("9780141439587", 1L)).thenReturn(true);

        assertThatThrownBy(() -> bookService.update(1L, request("9780141439587")))
                .isInstanceOf(DuplicateIsbnException.class);
        verify(bookRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateReplacesFieldsAndKeepsCreatedAt() {
        Book existing = book(1L, "Dune", "9780441013593");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(bookRepository.existsByIsbnAndIdNot("9780132350884", 1L)).thenReturn(false);
        when(bookRepository.saveAndFlush(existing)).thenReturn(existing);

        BookResponse response = bookService.update(1L, request("9780132350884"));

        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.author()).isEqualTo("Robert C. Martin");
        assertThat(response.isbn()).isEqualTo("9780132350884");
        assertThat(response.available()).isTrue();
    }

    @Test
    void deleteRejectsBookOnLoan() {
        when(bookRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(book(3L, "Dune", "9780441013593")));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(3L)).thenReturn(true);

        assertThatThrownBy(() -> bookService.delete(3L))
                .isInstanceOf(BookOnLoanException.class)
                .hasMessage("Book 3 is currently borrowed and cannot be deleted until it is returned");
        verify(bookRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesAvailableBook() {
        Book book = book(3L, "Dune", "9780441013593");
        when(bookRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(book));
        when(loanRepository.existsByBookIdAndReturnedAtIsNull(3L)).thenReturn(false);

        bookService.delete(3L);

        verify(bookRepository).delete(book);
    }

    @Test
    void deleteThrowsWhenBookDoesNotExist() {
        when(bookRepository.findByIdForUpdate(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.delete(3L)).isInstanceOf(BookNotFoundException.class);
    }
}
