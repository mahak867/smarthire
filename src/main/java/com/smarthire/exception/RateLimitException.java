// ── SmartHire · exception/RateLimitException.java ──
package com.smarthire.exception;
import lombok.Getter;
@Getter
public class RateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    public RateLimitException(String msg, long retryAfterSeconds) {
        super(msg);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
