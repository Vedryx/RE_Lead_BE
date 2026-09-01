package com.vedryxtech.voiceagent.apikey.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * The API key the AI voice agent authenticates with.
 *
 * <p>{@code apiKey} is populated <b>only</b> in the response that creates it. Afterwards only
 * the prefix is ever returned, because the key itself is stored hashed and cannot be read back.</p>
 */
public record ApiKeyResponse(

        @Schema(description = "The full key. Shown once, at creation. Copy it now.",
                example = "vdx_9f3aK2mQ...")
        String apiKey,

        @Schema(description = "First few characters, so you can tell which key is in use",
                example = "vdx_9f3aK2mQ")
        String prefix,

        OffsetDateTime createdAt,

        @Schema(description = "How to use it", example = "Send header: X-API-Key: <apiKey>")
        String usage
) {

    private static final String USAGE = "Send it on every request as the header  X-API-Key: <apiKey>";

    public static ApiKeyResponse created(String apiKey, String prefix, OffsetDateTime createdAt) {
        return new ApiKeyResponse(apiKey, prefix, createdAt, USAGE);
    }

    /** Without the key itself - all that can be shown once it has been created. */
    public static ApiKeyResponse existing(String prefix, OffsetDateTime createdAt) {
        return new ApiKeyResponse(null, prefix, createdAt, USAGE);
    }
}
