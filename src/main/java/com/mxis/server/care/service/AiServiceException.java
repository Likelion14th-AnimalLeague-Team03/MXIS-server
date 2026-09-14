package com.mxis.server.care.service;

/** Safe internal failure classification; never includes upstream response bodies or keys. */
public class AiServiceException extends RuntimeException {
    public enum Category { TIMEOUT, UNAVAILABLE, INVALID_RESPONSE, INTERRUPTED }
    private final Category category;

    public AiServiceException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public AiServiceException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category getCategory() { return category; }
}
