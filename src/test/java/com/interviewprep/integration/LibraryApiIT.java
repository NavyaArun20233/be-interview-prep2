package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.library.BookResponse;
import com.interviewprep.dto.library.LoanResponse;
import com.interviewprep.repository.BookRepository;
import com.interviewprep.repository.LoanRepository;
import java.time.Clock;
import java.time.Year;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Full stack: HTTP -> controller -> services -> JPA -> PostgreSQL schema created by Flyway (ddl-auto=validate). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LibraryApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private Flyway flyway;

    @Autowired
    private Clock clock;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        loanRepository.deleteAll();
        bookRepository.deleteAll();
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void flywayMigrationIsApplied() {
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("1");
    }

    @Test
    void crudRoundTrip() {
        BookResponse created = createBook("Dune", "Frank Herbert", "978-0-441-01359-3", "1965");
        assertThat(created.id()).isNotNull();
        assertThat(created.isbn()).isEqualTo("9780441013593");
        assertThat(created.publishedYear()).isEqualTo(1965);
        assertThat(created.available()).isTrue();

        client.get()
                .uri("/api/v1/books/{id}", created.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Dune")
                .jsonPath("$.author")
                .isEqualTo("Frank Herbert");

        BookResponse updated = client.put()
                .uri("/api/v1/books/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Dune Messiah", "author": "Frank Herbert", "isbn": "9780441172696"}
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(BookResponse.class)
                .getResponseBody();
        assertThat(updated.title()).isEqualTo("Dune Messiah");
        assertThat(updated.isbn()).isEqualTo("9780441172696");
        assertThat(updated.publishedYear()).isNull();
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());

        client.delete()
                .uri("/api/v1/books/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();

        client.get()
                .uri("/api/v1/books/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Book " + created.id() + " not found");
    }

    @Test
    void duplicateIsbnReturns409EvenWithDifferentSeparators() {
        createBook("Dune", "Frank Herbert", "9780441013593", null);

        client.post()
                .uri("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"title": "Dune (copy)", "author": "F. Herbert", "isbn": "978 0441 013593"}
                        """)
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("A book with ISBN 9780441013593 already exists");
    }

    @Test
    void futurePublishedYearReturns400() {
        String nextYear = Year.now(clock).plusYears(1).toString();

        client.post()
                .uri("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\": \"T\", \"author\": \"A\", \"isbn\": \"9780441013593\", \"publishedYear\": "
                        + nextYear + "}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("publishedYear");

        assertThat(bookRepository.count()).isZero();
    }

    @Test
    void searchMatchesTitleOrAuthorCaseInsensitivelyAndPaginates() {
        createBook("Dune", "Frank Herbert", "9780441013593", null);
        createBook("Emma", "Jane Austen", "9780141439587", null);
        createBook("Persuasion", "Jane Austen", "9780141439686", null);
        createBook("100% Pure", "Someone", "9780000000002", null);

        client.get()
                .uri("/api/v1/books?q=DUNE")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("Dune");

        client.get()
                .uri("/api/v1/books?q=austen&size=1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].title")
                .isEqualTo("Emma")
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.totalPages")
                .isEqualTo(2);

        // '%' is matched literally, not as a LIKE wildcard.
        client.get()
                .uri("/api/v1/books?q={q}", "%")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1);

        client.get()
                .uri("/api/v1/books")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(4);
    }

    @Test
    void borrowedBookCannotBeBorrowedAgainOrDeletedUntilReturned() {
        BookResponse book = createBook("Dune", "Frank Herbert", "9780441013593", null);

        LoanResponse loan = borrow(book.id(), "Ada Lovelace")
                .expectStatus()
                .isOk()
                .returnResult(LoanResponse.class)
                .getResponseBody();
        assertThat(loan.bookId()).isEqualTo(book.id());
        assertThat(loan.memberName()).isEqualTo("Ada Lovelace");
        assertThat(loan.borrowedAt()).isNotNull();
        assertThat(loan.returnedAt()).isNull();

        client.get()
                .uri("/api/v1/books/{id}", book.id())
                .exchange()
                .expectBody()
                .jsonPath("$.available")
                .isEqualTo(false);
        client.get()
                .uri("/api/v1/books")
                .exchange()
                .expectBody()
                .jsonPath("$.content[0].available")
                .isEqualTo(false);

        borrow(book.id(), "Alan Turing")
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(409)
                .jsonPath("$.detail")
                .isEqualTo(
                        "Book " + book.id() + " is already borrowed and cannot be borrowed again until it is returned")
                .jsonPath("$.instance")
                .isEqualTo("/api/v1/books/" + book.id() + "/borrow");

        client.delete()
                .uri("/api/v1/books/{id}", book.id())
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Book " + book.id() + " is currently borrowed and cannot be deleted until it is returned");

        LoanResponse returned = returnBook(book.id())
                .expectStatus()
                .isOk()
                .returnResult(LoanResponse.class)
                .getResponseBody();
        assertThat(returned.id()).isEqualTo(loan.id());
        assertThat(returned.returnedAt()).isAfterOrEqualTo(loan.borrowedAt());

        returnBook(book.id()).expectStatus().isEqualTo(409);

        borrow(book.id(), "Alan Turing").expectStatus().isOk();
        returnBook(book.id()).expectStatus().isOk();

        // Not on loan any more: the book (and its loan history) can be deleted.
        client.delete()
                .uri("/api/v1/books/{id}", book.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        assertThat(loanRepository.count()).isZero();
    }

    @Test
    void borrowingUnknownBookReturns404AndBlankMemberReturns400() {
        borrow(999_999L, "Ada").expectStatus().isNotFound();

        BookResponse book = createBook("Dune", "Frank Herbert", "9780441013593", null);
        client.post()
                .uri("/api/v1/books/{id}/borrow", book.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"memberName\": \"  \"}")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("memberName");
    }

    private BookResponse createBook(String title, String author, String isbn, String publishedYear) {
        String year = publishedYear == null ? "" : ", \"publishedYear\": " + publishedYear;
        return client.post()
                .uri("/api/v1/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"title\": \"%s\", \"author\": \"%s\", \"isbn\": \"%s\"%s}"
                        .formatted(title, author, isbn, year))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists("Location")
                .returnResult(BookResponse.class)
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec borrow(long bookId, String memberName) {
        return client.post()
                .uri("/api/v1/books/{id}/borrow", bookId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"memberName\": \"" + memberName + "\"}")
                .exchange();
    }

    private RestTestClient.ResponseSpec returnBook(long bookId) {
        return client.post().uri("/api/v1/books/{id}/return", bookId).exchange();
    }
}
