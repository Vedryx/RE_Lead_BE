package com.vedryxtech.voiceagent.exception;

/** Thrown when an authenticated caller reaches for something outside its organization. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
