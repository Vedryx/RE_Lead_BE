package com.vedryxtech.voiceagent.exception;

/** Thrown when the dialler is asked for a state move the lead's current state does not allow. */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
