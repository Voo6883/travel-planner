package com.travelplanner.api.dto.page;

/**
 * Pagination envelope fields returned by every list endpoint (PLAN §6.1). Serialised as
 * {@code page}, {@code page_size}, {@code total} by the global snake_case naming strategy.
 *
 * <p>A list response is this metadata plus its own typed {@code items} array. The array is not
 * modelled here because OpenAPI 3.0 has no generics: a shared {@code PageResponse<T>} would have
 * to degrade to {@code items: object} in the contract, erasing the element type in the generated
 * client — the precise drift codegen exists to prevent. Each list endpoint therefore declares its
 * own response record composing this one.
 */
public record PageMetadata(int page, int pageSize, long total) {

    public PageMetadata {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }

    public static PageMetadata of(PageQuery query, long total) {
        return new PageMetadata(query.page(), query.pageSize(), total);
    }
}
