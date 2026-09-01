package com.vedryxtech.voiceagent.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class ApiErrorFactory {

    public ApiError create(HttpStatus status, String message, String path) {
        return create(status, message, path, Map.of());
    }

    public ApiError create(HttpStatus status, String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors == null ? Map.of() : fieldErrors);
    }
}
