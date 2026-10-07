package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.TestcontainersConfiguration;
import com.interviewprep.dto.expense.ExpenseResponse;
import com.interviewprep.dto.expense.ExpenseSummaryResponse;
import com.interviewprep.entity.ExpenseCategory;
import com.interviewprep.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Full stack: HTTP -> controller -> service -> JPA -> PostgreSQL schema created by Flyway (ddl-auto=validate). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ExpenseApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private Flyway flyway;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void flywayMigrationIsAppliedAndSchemaValidates() {
        // The context only starts if Hibernate's ddl-auto=validate accepts the Flyway-created schema.
        assertThat(flyway.info().applied())
                .extracting(info -> info.getVersion().getVersion())
                .contains("2");
    }

    @Test
    void crudRoundTrip() {
        EntityExchangeResult<ExpenseResponse> createResult = client.post()
                .uri("/api/v1/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"amount": 12.5, "category": "FOOD", "date": "2026-10-05", "note": "Lunch"}
                        """)
                .exchange()
                .expectStatus()
                .isCreated()
                .returnResult(ExpenseResponse.class);
        ExpenseResponse created = createResult.getResponseBody();
        assertThat(created.id()).isNotNull();
        assertThat(created.amount()).isEqualTo(new BigDecimal("12.50"));
        assertThat(createResult.getResponseHeaders().getLocation()).hasPath("/api/v1/expenses/" + created.id());

        ExpenseResponse fetched = client.get()
                .uri("/api/v1/expenses/{id}", created.id())
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ExpenseResponse.class)
                .getResponseBody();
        assertThat(fetched).isEqualTo(created);

        ExpenseResponse updated = client.put()
                .uri("/api/v1/expenses/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"amount": "40.10", "category": "TRAVEL", "date": "2026-10-06"}
                        """)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ExpenseResponse.class)
                .getResponseBody();
        assertThat(updated.amount()).isEqualTo(new BigDecimal("40.10"));
        assertThat(updated.category()).isEqualTo(ExpenseCategory.TRAVEL);
        assertThat(updated.date()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(updated.note()).isNull();
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());

        client.delete()
                .uri("/api/v1/expenses/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();
        client.get()
                .uri("/api/v1/expenses/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNotFound();
        client.delete()
                .uri("/api/v1/expenses/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void invalidAmountIsRejectedAndNothingIsStored() {
        client.post()
                .uri("/api/v1/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"amount": 0.001, "category": "FOOD", "date": "2026-10-05"}
                        """)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("amount");

        assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void listFiltersByInclusiveDateRangeAndCategory() {
        create("1.00", "FOOD", "2026-09-30");
        create("2.00", "FOOD", "2026-10-01");
        create("3.00", "TRAVEL", "2026-10-15");
        create("4.00", "FOOD", "2026-10-31");
        create("5.00", "FOOD", "2026-11-01");

        // Both bounds inclusive, newest first.
        client.get()
                .uri("/api/v1/expenses?from=2026-10-01&to=2026-10-31")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(3)
                .jsonPath("$.content[0].date")
                .isEqualTo("2026-10-31")
                .jsonPath("$.content[2].date")
                .isEqualTo("2026-10-01");

        client.get()
                .uri("/api/v1/expenses?from=2026-10-01&to=2026-10-31&category=FOOD&size=1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.totalPages")
                .isEqualTo(2)
                .jsonPath("$.content.length()")
                .isEqualTo(1)
                .jsonPath("$.content[0].date")
                .isEqualTo("2026-10-31");

        client.get()
                .uri("/api/v1/expenses?category=TRAVEL")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.totalElements")
                .isEqualTo(1);

        client.get()
                .uri("/api/v1/expenses?from=2026-11-01&to=2026-10-01")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field")
                .isEqualTo("from");
    }

    @Test
    void summaryCountsFirstAndLastDayOfMonthAndAddsExactly() {
        create("100.00", "FOOD", "2026-01-31"); // day before: excluded
        create("0.10", "FOOD", "2026-02-01"); // first day
        create("0.20", "FOOD", "2026-02-28"); // last day
        create("5.55", "TRAVEL", "2026-02-14");
        create("100.00", "BILLS", "2026-03-01"); // day after: excluded

        String json = client.get()
                .uri("/api/v1/expenses/summary?month=2026-02")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        // Exact decimals on the wire: 0.30, not 0.30000000000000004 or 0.3.
        assertThat(json)
                .contains("\"month\":\"2026-02\"")
                .contains("\"FOOD\":0.30")
                .contains("\"BILLS\":0.00")
                .contains("\"total\":5.85");

        ExpenseSummaryResponse summary = summary("2026-02");
        assertThat(summary.totals())
                .containsExactly(
                        Map.entry(ExpenseCategory.FOOD, new BigDecimal("0.30")),
                        Map.entry(ExpenseCategory.TRAVEL, new BigDecimal("5.55")),
                        Map.entry(ExpenseCategory.BILLS, new BigDecimal("0.00")),
                        Map.entry(ExpenseCategory.OTHER, new BigDecimal("0.00")));
        assertThat(summary.total()).isEqualTo(new BigDecimal("5.85"));
    }

    @Test
    void summaryHandlesThirtyOneDayMonthAtYearEnd() {
        create("0.10", "OTHER", "2026-12-01");
        create("0.20", "OTHER", "2026-12-31");
        create("9.99", "OTHER", "2027-01-01");

        ExpenseSummaryResponse december = summary("2026-12");
        assertThat(december.totals()).containsEntry(ExpenseCategory.OTHER, new BigDecimal("0.30"));
        assertThat(december.total()).isEqualTo(new BigDecimal("0.30"));

        ExpenseSummaryResponse empty = summary("2026-06");
        assertThat(empty.total()).isEqualTo(new BigDecimal("0.00"));
        assertThat(empty.totals()).hasSize(4);
    }

    @Test
    void summaryRejectsInvalidMonth() {
        client.get()
                .uri("/api/v1/expenses/summary?month=2026-13")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    private void create(String amount, String category, String date) {
        client.post()
                .uri("/api/v1/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"amount": %s, "category": "%s", "date": "%s"}
                        """.formatted(amount, category, date))
                .exchange()
                .expectStatus()
                .isCreated();
    }

    private ExpenseSummaryResponse summary(String month) {
        return client.get()
                .uri("/api/v1/expenses/summary?month={month}", month)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(ExpenseSummaryResponse.class)
                .getResponseBody();
    }
}
