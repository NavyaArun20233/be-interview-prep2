package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.dto.quote.QuoteResponse;
import org.junit.jupiter.api.Test;

class QuoteServiceTest {

    @Test
    void returnsTheQuoteAtTheRandomlyChosenIndex() {
        QuoteService service = new QuoteService(bound -> bound - 1);

        assertThat(service.randomQuote()).isEqualTo(QuoteService.QUOTES.getLast());
    }

    @Test
    void defaultRandomSourceAlwaysReturnsAKnownQuote() {
        QuoteService service = new QuoteService();

        for (int i = 0; i < 50; i++) {
            QuoteResponse quote = service.randomQuote();
            assertThat(QuoteService.QUOTES).contains(quote);
        }
    }
}
