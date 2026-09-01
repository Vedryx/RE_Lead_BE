package com.vedryxtech.voiceagent.exception;

/** Thrown when a request carries no usable identity or tenant. Rendered as 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
