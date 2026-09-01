package com.vedryxtech.voiceagent.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final ApiErrorFactory apiErrorFactory;

    public ApiErrorResponseWriter(ObjectMapper objectMapper, ApiErrorFactory apiErrorFactory) {
        this.objectMapper = objectMapper;
        this.apiErrorFactory = apiErrorFactory;
    }

    public void write(HttpServletResponse response, String path, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), apiErrorFactory.create(status, message, path));
    }
}
