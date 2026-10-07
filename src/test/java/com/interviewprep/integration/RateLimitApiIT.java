package com.interviewprep.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.MutableClock;
import com.interviewprep.TestcontainersConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Quotes API over real HTTP with the default quota (10 per minute) and a clock the test controls. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, RateLimitApiIT.ClockOverride.class})
class RateLimitApiIT {

    private static final String RANDOM_QUOTE = "/api/v1/quotes/random";

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockOverride {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableClock clock;

    @Autowired
    private Clock appClock;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void applicationUsesTheControllableClock() {
        assertThat(appClock).isSameAs(clock);
    }

    @Test
    void eleventhRequestInAMinuteIsRejectedWith429AndRetryAfter() {
        String apiKey = uniqueKey();
        for (int i = 1; i <= 10; i++) {
            client.get()
                    .uri(RANDOM_QUOTE)
                    .header("X-API-Key", apiKey)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .valueEquals("X-RateLimit-Remaining", Integer.toString(10 - i))
                    .expectBody()
                    .jsonPath("$.text")
                    .isNotEmpty()
                    .jsonPath("$.author")
                    .isNotEmpty();
        }

        client.get()
                .uri(RANDOM_QUOTE)
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .valueEquals(HttpHeaders.RETRY_AFTER, "60")
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(429)
                .jsonPath("$.detail")
                .isEqualTo("Rate limit of 10 requests per 60 seconds exceeded; retry after 60 seconds")
                .jsonPath("$.retryAfterSeconds")
                .isEqualTo(60)
                .jsonPath("$.instance")
                .isEqualTo(RANDOM_QUOTE);

        // Another client is unaffected by the exhausted quota.
        client.get()
                .uri(RANDOM_QUOTE)
                .header("X-API-Key", uniqueKey())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-RateLimit-Remaining", "9");

        // Once the window has passed, the first client is allowed again.
        clock.advance(Duration.ofMinutes(1));
        client.get()
                .uri(RANDOM_QUOTE)
                .header("X-API-Key", apiKey)
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void missingApiKeyIsRejectedWith401() {
        client.get()
                .uri(RANDOM_QUOTE)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail")
                .isEqualTo("Missing or blank X-API-Key header; send your API key in it");
    }

    @Test
    void endpointsOutsideTheQuotesApiAreNotRateLimited() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    private static String uniqueKey() {
        return "it-" + UUID.randomUUID();
    }
}
