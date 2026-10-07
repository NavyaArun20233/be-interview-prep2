package com.interviewprep.service;

import com.interviewprep.config.RateLimitProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * Sliding-window-log rate limiter: each client key may make at most {@code limit} requests in any interval of length
 * {@code window}.
 *
 * <p>Per key it keeps the timestamps of the requests still inside the window. Check-and-record runs inside
 * {@link ConcurrentHashMap#compute}, which is atomic per key, so simultaneous requests from one client can never
 * exceed the limit, while different clients do not block each other. Rejected requests are not recorded, so a client
 * that keeps retrying is not locked out for longer than the window.
 *
 * <p>State is held in this JVM only: with several application instances each one enforces its own quota. Scaling out
 * would need a shared store such as Redis.
 */
@Service
public class RateLimiterService {

    private final Clock clock;
    private final int limit;
    private final Duration window;
    private final ConcurrentMap<String, Deque<Instant>> requestLogs = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> nextIdleSweep;

    public RateLimiterService(RateLimitProperties properties, Clock clock) {
        this.clock = clock;
        this.limit = properties.limit();
        this.window = properties.window();
        this.nextIdleSweep = new AtomicReference<>(clock.instant().plus(window));
    }

    /** Records a request for {@code clientKey} if it is within quota and reports the outcome. */
    public Decision tryAcquire(String clientKey) {
        Instant now = clock.instant();
        Decision[] decision = new Decision[1];
        requestLogs.compute(clientKey, (key, existing) -> {
            Deque<Instant> timestamps = existing != null ? existing : new ArrayDeque<>();
            dropExpired(timestamps, now);
            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                decision[0] = new Decision(true, limit, window, limit - timestamps.size(), Duration.ZERO);
            } else {
                Duration untilOldestExpires =
                        Duration.between(now, timestamps.peekFirst().plus(window));
                decision[0] = new Decision(false, limit, window, 0, untilOldestExpires);
            }
            return timestamps;
        });
        evictIdleClientsIfDue(now);
        return decision[0];
    }

    /** Number of client keys currently tracked; exposed for tests of idle-key eviction. */
    int trackedClients() {
        return requestLogs.size();
    }

    /**
     * At most once per window, removes clients with no request inside the window so memory stays bounded by the number
     * of recently active keys. Only the thread that wins the compare-and-set sweeps.
     */
    private void evictIdleClientsIfDue(Instant now) {
        Instant due = nextIdleSweep.get();
        if (now.isBefore(due) || !nextIdleSweep.compareAndSet(due, now.plus(window))) {
            return;
        }
        for (String key : requestLogs.keySet()) {
            requestLogs.computeIfPresent(key, (k, timestamps) -> {
                dropExpired(timestamps, now);
                return timestamps.isEmpty() ? null : timestamps;
            });
        }
    }

    /** A request made at {@code t} counts against the quota until {@code t + window}. */
    private void dropExpired(Deque<Instant> timestamps, Instant now) {
        while (!timestamps.isEmpty() && !timestamps.peekFirst().plus(window).isAfter(now)) {
            timestamps.pollFirst();
        }
    }

    /**
     * Outcome of {@link #tryAcquire}.
     *
     * @param remaining requests still allowed in the current window after this one
     * @param retryAfter time until the oldest counted request leaves the window; zero when allowed
     */
    public record Decision(boolean allowed, int limit, Duration window, int remaining, Duration retryAfter) {

        /** {@code retryAfter} rounded up to whole seconds (at least 1) for {@code Retry-After}; 0 when allowed. */
        public long retryAfterSeconds() {
            if (allowed) {
                return 0;
            }
            long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
            return Math.max(1, seconds);
        }
    }
}
