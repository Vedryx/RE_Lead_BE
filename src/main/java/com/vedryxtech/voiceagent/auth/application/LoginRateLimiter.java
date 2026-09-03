package com.vedryxtech.voiceagent.auth.application;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory sliding-window rate limiter for {@code POST /auth/login} (M-12).
 *
 * <p>BCrypt gives the app a natural per-request cost of ~100 ms, but that alone is not a
 * throttle — 30 wrong-password attempts in three seconds cost the CRM nothing and cost an
 * attacker one whoami round trip. This limiter blocks after {@link #MAX_FAILURES} failures
 * inside {@link #WINDOW}, keyed by the presented email (lower-cased).
 *
 * <p>Deliberately in-memory: this app runs single-instance, and putting the counter in
 * Mongo would trade a login latency win for one that persists across restarts we do not
 * need. If it ever runs multi-instance a shared cache goes here, not the DB.
 *
 * <p>Non-enumeration is preserved by the caller: the block message is generic ("Too many
 * login attempts") so an attacker still cannot tell a real account from a made-up one.
 */
@Component
public class LoginRateLimiter {

    /** Failures inside {@link #WINDOW} before we start refusing. */
    private static final int MAX_FAILURES = 5;
    /** How far back we look for failures. */
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /**
     * @return true if the key has hit {@link #MAX_FAILURES} inside {@link #WINDOW}.
     */
    public boolean isBlocked(String rawKey) {
        String key = normalise(rawKey);
        if (key == null) {
            return false;
        }
        return sizeInWindow(key) >= MAX_FAILURES;
    }

    /** Called once per bad password / unknown-email attempt. */
    public void recordFailure(String rawKey) {
        String key = normalise(rawKey);
        if (key == null) {
            return;
        }
        Deque<Instant> queue = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(Instant.now());
            trim(queue);
        }
    }

    /** Successful login wipes the streak. */
    public void reset(String rawKey) {
        String key = normalise(rawKey);
        if (key == null) {
            return;
        }
        failures.remove(key);
    }

    private int sizeInWindow(String key) {
        Deque<Instant> queue = failures.get(key);
        if (queue == null) {
            return 0;
        }
        synchronized (queue) {
            trim(queue);
            return queue.size();
        }
    }

    private void trim(Deque<Instant> queue) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
            queue.pollFirst();
        }
    }

    private static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
