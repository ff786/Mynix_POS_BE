package com.mynix.backend.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing. Failed logins are counted per username +
 * client address and per client address; over the limit, logins are refused
 * for the rest of the window. In memory: the POS runs as one instance.
 */
@Service
public class LoginAttemptService {

    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int MAX_PER_USER_AND_ADDRESS = 5;
    static final int MAX_PER_ADDRESS = 20;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isBlocked(String username, String address) {
        return count(userKey(username, address)) >= MAX_PER_USER_AND_ADDRESS
                || count(addressKey(address)) >= MAX_PER_ADDRESS;
    }

    public void recordFailure(String username, String address) {
        record(userKey(username, address));
        record(addressKey(address));
    }

    public void recordSuccess(String username, String address) {
        failures.remove(userKey(username, address));
    }

    private int count(String key) {
        Deque<Instant> times = failures.get(key);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }
            return times.size();
        }
    }

    private void record(String key) {
        Deque<Instant> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(Instant.now());
            while (times.size() > 100) {
                times.pollFirst();
            }
        }
    }

    private static String userKey(String username, String address) {
        return "u:" + (username == null ? "" : username.toLowerCase()) + "|" + address;
    }

    private static String addressKey(String address) {
        return "a:" + address;
    }
}
