package com.bookbridge.request;

/**
 * Lifecycle of a resource request. OPEN requests accept chat responses;
 * FULFILLED / CANCELLED / EXPIRED are terminal and closed to responses.
 */
public enum RequestStatus {
    OPEN, FULFILLED, CANCELLED, EXPIRED
}
