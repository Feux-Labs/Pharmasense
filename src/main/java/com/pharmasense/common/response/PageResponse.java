package com.pharmasense.common.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Flattened page shape returned to clients instead of exposing Spring Data's
 * {@link Page} directly, keeping the wire contract stable if we swap the
 * pagination implementation later.
 */
public record PageResponse<T>(
        List<T> items,
        int pageNumber,
        int pageSize,
        long totalItems,
        int totalPages,
        boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
