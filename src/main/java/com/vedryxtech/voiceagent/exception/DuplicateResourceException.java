package com.vedryxtech.voiceagent.exception;

/** Thrown when a write would violate a unique key. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public static DuplicateResourceException callingPhone(String phone) {
        return new DuplicateResourceException(
                "A lead with calling phone '" + phone + "' already exists. "
                        + "Use PUT /api/v1/leads to upsert it, or GET /api/v1/leads?phone=... to find it first.");
    }

}
