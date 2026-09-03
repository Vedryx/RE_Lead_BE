package com.vedryxtech.voiceagent.exception;

/**
 * Thrown when a caller has exceeded a rate limit. Rendered as 429 by
 * {@code GlobalExceptionHandler}. The message stays generic on the login path so
 * an attacker cannot use it to enumerate accounts.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
