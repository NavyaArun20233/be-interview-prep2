package com.interviewprep.controller;

import com.interviewprep.dto.quote.QuoteResponse;
import com.interviewprep.service.QuoteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quotes API; every request is subject to the per-API-key quota enforced by {@link RateLimitInterceptor}. */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/random")
    QuoteResponse random() {
        return quoteService.randomQuote();
    }
}
