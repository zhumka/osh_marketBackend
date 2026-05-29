package com.oshmarket.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginRateLimitService {

    @Value("${app.rate-limit.max-attempts}")
    private int maxAttempts;

    @Value("${app.rate-limit.lockout-minutes}")
    private int lockoutMinutes;

    private final ConcurrentHashMap<String, List<Long>> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        List<Long> timestamps = getRecentAttempts(ip);
        return timestamps.size() >= maxAttempts;
    }

    public void recordAttempt(String ip) {
        attempts.computeIfAbsent(ip, k -> new ArrayList<>()).add(Instant.now().toEpochMilli());
    }

    public void clearAttempts(String ip) {
        attempts.remove(ip);
    }

    private List<Long> getRecentAttempts(String ip) {
        long cutoff = Instant.now().minusSeconds((long) lockoutMinutes * 60).toEpochMilli();
        List<Long> all = attempts.getOrDefault(ip, new ArrayList<>());
        List<Long> recent = all.stream().filter(t -> t > cutoff).toList();
        attempts.put(ip, new ArrayList<>(recent));
        return recent;
    }
}
