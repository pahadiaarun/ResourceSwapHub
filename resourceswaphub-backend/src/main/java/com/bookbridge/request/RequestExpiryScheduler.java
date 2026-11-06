package com.bookbridge.request;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hourly sweep that closes overdue requests whose status has not been touched
 * by a read path yet. Request expiry itself is enforced lazily on every browse
 * and read, so correctness does not depend on this run; a paused host only
 * delays the visible status flip.
 */
@Component
public class RequestExpiryScheduler {

    private final ResourceRequestService requestService;

    public RequestExpiryScheduler(ResourceRequestService requestService) {
        this.requestService = requestService;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    @Transactional
    public void expireOverdue() {
        requestService.expireDue();
    }

    /**
     * Lightweight liveness pings run every 10 seconds. They do not perform any
     * application work — they only tick the {@link #lastPing} heartbeat so outside
     * monitors can distinguish a hung JVM from a quiet one.
     */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void livenessPing() {
        lastPing.set(Instant.now());
    }

    private final AtomicReference<Instant> lastPing = new AtomicReference<>();

    public Optional<Instant> lastPing() {
        return Optional.ofNullable(lastPing.get());
    }
}
