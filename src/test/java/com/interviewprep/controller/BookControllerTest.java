package com.interviewprep.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.config.ValidationConfig;
import com.interviewprep.dto.PageResponse;
import com.interviewprep.dto.library.BookRequest;
import com.interviewprep.dto.library.BookResponse;
import com.interviewprep.dto.library.BorrowRequest;
import com.interviewprep.dto.library.LoanResponse;
import com.interviewprep.exception.BookAlreadyBorrowedException;
import com.interviewprep.exception.BookNotBorrowedException;
import com.interviewprep.exception.BookNotFoundException;
import com.interviewprep.exception.BookOnLoanException;
import com.interviewprep.exception.DuplicateIsbnException;
import com.interviewprep.service.BookService;
import com.interviewprep.service.LoanService;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookController.class)
@Import({ValidationConfig.class, BookControllerTest.FixedClockConfig.class})
class BookControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private LoanService loanService;

    private static BookResponse book(long id, boolean available) {
        return new BookResponse(id, "Dune", "Frank Herbert", "9780441013593", 1965, available, NOW, NOW);
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(bookService.create(any(BookRequest.class))).thenReturn(book(1L, true));

        // A book published in the current year of the injected clock is accepted.
        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dune", "author": "Frank Herbert", "isbn": "978-0441013593",
                                 "publishedYear": 2026}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/books/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isbn").value("9780441013593"))
                .andExpect(jsonPath("$.publishedYear").value(1965))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));

        verify(bookService).create(new BookRequest("Dune", "Frank Herbert", "978-0441013593", Year.of(2026)));
    }

    @Test
    void createRejectsMissingFieldsAndFutureYearWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": " ", "isbn": "978-0441013593", "publishedYear": 2027}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/v1/books"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'author')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'publishedYear')].message")
                        .value("must be a date in the past or in the present"));

        verifyNoInteractions(bookService);
    }

    @Test
    void createRejectsMalformedIsbn() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dune", "author": "Frank Herbert", "isbn": "12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("isbn"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be an ISBN-10 or ISBN-13 (digits, optionally separated by hyphens or spaces)"));

        verifyNoInteractions(bookService);
    }

    @Test
    void createRejectsNonNumericYear() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dune", "author": "Frank Herbert", "isbn": "9780441013593",
                                 "publishedYear": "soon"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("publishedYear"));

        verifyNoInteractions(bookService);
    }

    @Test
    void createWithDuplicateIsbnReturns409ProblemDetail() throws Exception {
        when(bookService.create(any(BookRequest.class))).thenThrow(new DuplicateIsbnException("9780441013593"));

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dune", "author": "Frank Herbert", "isbn": "9780441013593"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A book with ISBN 9780441013593 already exists"));
    }

    @Test
    void getUnknownBookReturns404ProblemDetail() throws Exception {
        when(bookService.get(42L)).thenThrow(new BookNotFoundException(42L));

        mockMvc.perform(get("/api/v1/books/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Book 42 not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/books/42"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void listPassesSearchTermAndPaging() throws Exception {
        when(bookService.list(Optional.of("herbert"), 1, 5))
                .thenReturn(new PageResponse<>(List.of(book(3L, false)), 1, 5, 6, 2));

        mockMvc.perform(get("/api/v1/books")
                        .param("q", "herbert")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].available").value(false))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listUsesDefaultPagingWithoutSearch() throws Exception {
        when(bookService.list(Optional.empty(), 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/books")).andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));

        verify(bookService).list(Optional.empty(), 0, 20);
    }

    @Test
    void listRejectsPageSizeAboveMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));

        verifyNoInteractions(bookService);
    }

    @Test
    void updateReturnsUpdatedBook() throws Exception {
        when(bookService.update(eq(5L), any(BookRequest.class))).thenReturn(book(5L, true));

        mockMvc.perform(put("/api/v1/books/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Dune", "author": "Frank Herbert", "isbn": "9780441013593"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/books/9")).andExpect(status().isNoContent());

        verify(bookService).delete(9L);
    }

    @Test
    void deleteBorrowedBookReturns409() throws Exception {
        doThrow(new BookOnLoanException(9L)).when(bookService).delete(9L);

        mockMvc.perform(delete("/api/v1/books/9"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail")
                        .value("Book 9 is currently borrowed and cannot be deleted until it is returned"));
    }

    @Test
    void borrowReturnsOpenLoan() throws Exception {
        when(loanService.borrow(5L, new BorrowRequest("Ada Lovelace")))
                .thenReturn(new LoanResponse(11L, 5L, "Ada Lovelace", NOW, null));

        mockMvc.perform(post("/api/v1/books/5/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberName": "Ada Lovelace"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.bookId").value(5))
                .andExpect(jsonPath("$.memberName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.borrowedAt").value("2026-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.returnedAt").doesNotExist());
    }

    @Test
    void borrowRequiresMemberName() throws Exception {
        mockMvc.perform(post("/api/v1/books/5/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("memberName"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));

        verifyNoInteractions(loanService);
    }

    @Test
    void borrowUnavailableBookReturns409ProblemDetail() throws Exception {
        when(loanService.borrow(eq(5L), any(BorrowRequest.class))).thenThrow(new BookAlreadyBorrowedException(5L));

        mockMvc.perform(post("/api/v1/books/5/borrow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberName": "Ada Lovelace"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail")
                        .value("Book 5 is already borrowed and cannot be borrowed again until it is returned"))
                .andExpect(jsonPath("$.instance").value("/api/v1/books/5/borrow"));
    }

    @Test
    void returnBookThatIsNotBorrowedReturns409() throws Exception {
        when(loanService.returnBook(5L)).thenThrow(new BookNotBorrowedException(5L));

        mockMvc.perform(post("/api/v1/books/5/return"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Book 5 is not currently borrowed, so it cannot be returned"));
    }

    @Test
    void returnClosesLoan() throws Exception {
        when(loanService.returnBook(5L)).thenReturn(new LoanResponse(11L, 5L, "Ada Lovelace", NOW, NOW));

        mockMvc.perform(post("/api/v1/books/5/return"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt").value("2026-01-15T10:00:00Z"));
    }
}
