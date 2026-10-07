package com.interviewprep.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.interviewprep.dto.quote.QuoteResponse;
import com.interviewprep.service.QuoteService;
import com.interviewprep.service.RateLimiterService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Quotes endpoint behind {@link RateLimitInterceptor}, with the limiter mocked. */
@WebMvcTest(QuoteController.class)
@Import(RateLimitWebConfig.class)
class QuoteControllerTest {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteService quoteService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @Test
    void allowedRequestReturnsQuoteWithRateLimitHeaders() throws Exception {
        given(rateLimiterService.tryAcquire("key-1"))
                .willReturn(new RateLimiterService.Decision(true, 10, WINDOW, 7, Duration.ZERO));
        given(quoteService.randomQuote()).willReturn(new QuoteResponse("Talk is cheap.", "Linus Torvalds"));

        mockMvc.perform(get("/api/v1/quotes/random").header("X-API-Key", " key-1 "))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-RateLimit-Limit", "10"))
                .andExpect(header().string("X-RateLimit-Remaining", "7"))
                .andExpect(jsonPath("$.text").value("Talk is cheap."))
                .andExpect(jsonPath("$.author").value("Linus Torvalds"));
        verify(rateLimiterService).tryAcquire("key-1");
    }

    @Test
    void exhaustedQuotaReturns429ProblemWithRetryAfter() throws Exception {
        given(rateLimiterService.tryAcquire(anyString()))
                .willReturn(new RateLimiterService.Decision(false, 10, WINDOW, 0, Duration.ofMillis(41_200)));

        mockMvc.perform(get("/api/v1/quotes/random").header("X-API-Key", "key-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail")
                        .value("Rate limit of 10 requests per 60 seconds exceeded; retry after 42 seconds"))
                .andExpect(jsonPath("$.instance").value("/api/v1/quotes/random"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(42));
        verifyNoInteractions(quoteService);
    }

    @Test
    void missingApiKeyReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/quotes/random"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"X-API-Key\""))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Missing or blank X-API-Key header; send your API key in it"))
                .andExpect(jsonPath("$.instance").value("/api/v1/quotes/random"));
        verifyNoInteractions(rateLimiterService, quoteService);
    }

    @Test
    void blankApiKeyReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/quotes/random").header("X-API-Key", "   ")).andExpect(status().isUnauthorized());
        verifyNoInteractions(rateLimiterService, quoteService);
    }
}
