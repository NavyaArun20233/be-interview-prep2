package com.interviewprep.service;

import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.library.BookRequest;
import com.interviewprep.dto.library.BookResponse;
import com.interviewprep.entity.Book;
import com.interviewprep.exception.BookNotFoundException;
import com.interviewprep.exception.BookOnLoanException;
import com.interviewprep.exception.DuplicateIsbnException;
import com.interviewprep.repository.BookRepository;
import com.interviewprep.repository.LoanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Book catalog: CRUD and search. Availability is derived from open loans. */
@Service
public class BookService {

    static final String ISBN_CONSTRAINT = "uq_books_isbn";

    private static final Logger log = LoggerFactory.getLogger(BookService.class);
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("title"), Sort.Order.asc("id"));

    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final Clock clock;

    public BookService(BookRepository bookRepository, LoanRepository loanRepository, Clock clock) {
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.clock = clock;
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        String isbn = request.normalizedIsbn();
        if (bookRepository.existsByIsbn(isbn)) {
            throw new DuplicateIsbnException(isbn);
        }
        Book book = new Book(request.title(), request.author(), isbn, request.publishedYearValue(), now());
        Book saved = saveAndFlush(book);
        log.info("Created book {}", saved.getId());
        return BookResponse.from(saved, true);
    }

    @Transactional(readOnly = true)
    public BookResponse get(long id) {
        Book book = findBook(id);
        return BookResponse.from(book, isAvailable(id));
    }

    /** Lists books, optionally only those whose title or author contains {@code query} (case-insensitive). */
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> list(Optional<String> query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, DEFAULT_SORT);
        Page<Book> books = query.map(String::strip)
                .filter(q -> !q.isEmpty())
                .map(q -> bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(q, q, pageRequest))
                .orElseGet(() -> bookRepository.findAll(pageRequest));
        Set<Long> onLoan = books.isEmpty()
                ? Set.of()
                : loanRepository.findBookIdsOnLoan(books.map(Book::getId).getContent());
        return PageResponse.from(books, book -> BookResponse.from(book, !onLoan.contains(book.getId())));
    }

    @Transactional
    public BookResponse update(long id, BookRequest request) {
        Book book = findBook(id);
        String isbn = request.normalizedIsbn();
        if (bookRepository.existsByIsbnAndIdNot(isbn, id)) {
            throw new DuplicateIsbnException(isbn);
        }
        book.update(request.title(), request.author(), isbn, request.publishedYearValue(), now());
        // Flush so the optimistic-lock version and the ISBN unique constraint are checked before we respond.
        Book saved = saveAndFlush(book);
        log.info("Updated book {}", id);
        return BookResponse.from(saved, isAvailable(id));
    }

    @Transactional
    public void delete(long id) {
        // Lock the row so a concurrent borrow cannot slip in between the check and the delete.
        Book book = bookRepository.findByIdForUpdate(id).orElseThrow(() -> new BookNotFoundException(id));
        if (!isAvailable(id)) {
            throw new BookOnLoanException(id);
        }
        bookRepository.delete(book);
        log.info("Deleted book {}", id);
    }

    private boolean isAvailable(long id) {
        return !loanRepository.existsByBookIdAndReturnedAtIsNull(id);
    }

    /** Saves and maps a lost race on the ISBN unique constraint (both requests passed the exists check) to 409. */
    private Book saveAndFlush(Book book) {
        try {
            return bookRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException ex) {
            if (violates(ex, ISBN_CONSTRAINT)) {
                throw new DuplicateIsbnException(book.getIsbn());
            }
            throw ex;
        }
    }

    private static boolean violates(Throwable ex, String constraintName) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    /** PostgreSQL TIMESTAMPTZ keeps microseconds; truncate so responses match what is persisted. */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Book findBook(long id) {
        return bookRepository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }
}
