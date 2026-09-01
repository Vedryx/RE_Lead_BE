package com.vedryxtech.voiceagent.exception;

/** Thrown when a payload is well-formed but invalid for its {@code actionType}. */
public class InvalidLeadPayloadException extends RuntimeException {

    public InvalidLeadPayloadException(String message) {
        super(message);
    }
}
