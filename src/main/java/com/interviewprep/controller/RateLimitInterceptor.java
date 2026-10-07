package com.interviewprep.controller;

import com.interviewprep.exception.MissingApiKeyException;
import com.interviewprep.exception.RateLimitExceededException;
import com.interviewprep.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the per-client quota before the controller runs. The client is identified by the {@code X-API-Key} header;
 * a missing or blank key is rejected with 401 and an exhausted quota with 429. Allowed responses carry
 * {@code X-RateLimit-Limit} and {@code X-RateLimit-Remaining}.
 *
 * <p>Exceptions thrown here go through the normal handler-exception resolution, so {@code GlobalExceptionHandler}
 * renders them as Problem Details. Registered for the quotes API by {@link RateLimitWebConfig}.
 */
class RateLimitInterceptor implements HandlerInterceptor {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private final RateLimiterService rateLimiterService;

    RateLimitInterceptor(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            throw new MissingApiKeyException(API_KEY_HEADER);
        }
        RateLimiterService.Decision decision = rateLimiterService.tryAcquire(apiKey.strip());
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.limit(), decision.window(), decision.retryAfterSeconds());
        }
        response.setHeader(LIMIT_HEADER, Integer.toString(decision.limit()));
        response.setHeader(REMAINING_HEADER, Integer.toString(decision.remaining()));
        return true;
    }
}
