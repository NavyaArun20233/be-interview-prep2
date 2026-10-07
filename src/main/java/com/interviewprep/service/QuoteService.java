package com.interviewprep.service;

import com.interviewprep.dto.quote.QuoteResponse;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;
import org.springframework.stereotype.Service;

/** Serves a random quote from a small fixed catalogue; exists to give the rate limiter an endpoint to protect. */
@Service
public class QuoteService {

    static final List<QuoteResponse> QUOTES = List.of(
            new QuoteResponse("Simplicity is prerequisite for reliability.", "Edsger W. Dijkstra"),
            new QuoteResponse("Premature optimization is the root of all evil.", "Donald Knuth"),
            new QuoteResponse("Talk is cheap. Show me the code.", "Linus Torvalds"),
            new QuoteResponse("Make it work, make it right, make it fast.", "Kent Beck"),
            new QuoteResponse(
                    "Any fool can write code that a computer can understand. "
                            + "Good programmers write code that humans can understand.",
                    "Martin Fowler"));

    /** Maps a bound {@code n} to an index in {@code [0, n)}; injectable so tests can choose the quote. */
    private final IntUnaryOperator randomIndex;

    public QuoteService() {
        this(bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    QuoteService(IntUnaryOperator randomIndex) {
        this.randomIndex = randomIndex;
    }

    public QuoteResponse randomQuote() {
        return QUOTES.get(randomIndex.applyAsInt(QUOTES.size()));
    }
}
