package com.vedryxtech.voiceagent.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when a payload is well-formed but fails a cross-field or bounds validation the DTO
 * cannot express. Rendered as HTTP 422 with a {@code fieldErrors} envelope so the caller sees
 * every problem at once, not just the first.
 */
public class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fieldErrors);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
