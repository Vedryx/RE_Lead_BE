package com.vedryxtech.voiceagent.exception;

/** Thrown when a document cannot be located by any of its identifiers, within the caller's tenant. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String entity, String field, String value) {
        return new ResourceNotFoundException("No " + entity + " found with " + field + " '" + value + "'");
    }

    public static ResourceNotFoundException lead(String field, String value) {
        return of("lead", field, value);
    }

    public static ResourceNotFoundException callLog(String field, String value) {
        return of("call log", field, value);
    }
}
