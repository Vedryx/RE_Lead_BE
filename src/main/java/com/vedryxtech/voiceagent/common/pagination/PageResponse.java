package com.vedryxtech.voiceagent.common.pagination;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Slim pagination envelope so clients are not coupled to Spring's Page serialization. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasNext
) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
