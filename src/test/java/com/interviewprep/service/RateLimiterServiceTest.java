package com.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.interviewprep.MutableClock;
import com.interviewprep.config.RateLimitProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-07T10:00:00Z"));
    private final RateLimiterService limiter = new RateLimiterService(new RateLimitProperties(LIMIT, WINDOW), clock);

    @Test
    void allowsLimitRequestsThenRejectsTheNextWithTimeUntilOldestExpires() {
        for (int i = 1; i <= LIMIT; i++) {
            RateLimiterService.Decision decision = limiter.tryAcquire("alice");
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remaining()).isEqualTo(LIMIT - i);
            clock.advance(Duration.ofSeconds(1));
        }

        RateLimiterService.Decision eleventh = limiter.tryAcquire("alice");

        // First request at 0s expires at 60s; it is now 10s.
        assertThat(eleventh.allowed()).isFalse();
        assertThat(eleventh.remaining()).isZero();
        assertThat(eleventh.retryAfter()).isEqualTo(Duration.ofSeconds(50));
        assertThat(eleventh.retryAfterSeconds()).isEqualTo(50);
        assertThat(eleventh.limit()).isEqualTo(LIMIT);
        assertThat(eleventh.window()).isEqualTo(WINDOW);
    }

    @Test
    void retryAfterIsRoundedUpToWholeSeconds() {
        exhaust("alice");
        clock.advance(Duration.ofMillis(17_500));

        RateLimiterService.Decision rejected = limiter.tryAcquire("alice");

        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMillis(42_500));
        assertThat(rejected.retryAfterSeconds()).isEqualTo(43);
    }

    @Test
    void retryAfterSecondsIsAtLeastOne() {
        exhaust("alice");
        clock.advance(WINDOW.minusMillis(1));

        assertThat(limiter.tryAcquire("alice").retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void windowSlidesSoRequestsAreAllowedAgainOnceOldOnesExpire() {
        limiter.tryAcquire("alice"); // at 0s
        clock.advance(Duration.ofSeconds(30));
        for (int i = 0; i < LIMIT - 1; i++) {
            limiter.tryAcquire("alice"); // at 30s
        }
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(30)); // 60s: only the request made at 0s has expired

        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        RateLimiterService.Decision rejected = limiter.tryAcquire("alice");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectedRequestsDoNotExtendTheLockout() {
        exhaust("alice");
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofSeconds(10));
            assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
        }

        clock.advance(Duration.ofSeconds(10)); // 60s after the first window filled

        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
    }

    @Test
    void differentKeysHaveIndependentQuotas() {
        exhaust("alice");
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();

        RateLimiterService.Decision bob = limiter.tryAcquire("bob");

        assertThat(bob.allowed()).isTrue();
        assertThat(bob.remaining()).isEqualTo(LIMIT - 1);
    }

    @Test
    void idleKeysAreEvictedAfterAWindow() {
        limiter.tryAcquire("alice");
        limiter.tryAcquire("bob");
        assertThat(limiter.trackedClients()).isEqualTo(2);

        clock.advance(WINDOW);
        limiter.tryAcquire("carol");

        assertThat(limiter.trackedClients()).isEqualTo(1);
    }

    @Test
    void simultaneousRequestsFromOneKeyNeverExceedTheLimit() throws Exception {
        int threads = 50;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return limiter.tryAcquire("alice").allowed();
                }));
            }
            ready.await();
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(LIMIT);
        } finally {
            executor.shutdownNow();
        }
    }

    private void exhaust(String key) {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.tryAcquire(key).allowed()).isTrue();
        }
    }
}
