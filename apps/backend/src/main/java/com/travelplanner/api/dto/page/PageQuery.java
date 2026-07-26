package com.travelplanner.api.dto.page;

import com.travelplanner.domain.exception.ValidationFailedException;
import java.util.regex.Pattern;

/**
 * Validated {@code page} / {@code page_size} / {@code sort} triple for list endpoints
 * (PLAN §6.1). Controllers bind the raw query parameters and call {@link #of}; services receive
 * this record and never see nulls or out-of-range values.
 *
 * <p>Out-of-range values are <em>rejected</em>, not clamped. Silently serving 100 items when the
 * caller asked for 5000 makes a client's paging arithmetic wrong in a way it cannot detect;
 * {@code 400 validation_failed} makes the mistake obvious at the first call.
 */
public record PageQuery(int page, int pageSize, String sort) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT = "-created_at";

    /** Mirrors the {@code SortParam} pattern in the OpenAPI contract. */
    private static final Pattern SORT = Pattern.compile("^-?[a-z][a-z0-9_]*$");

    /**
     * Builds a query from raw, possibly absent request parameters.
     *
     * @throws ValidationFailedException when a supplied value is outside the published contract
     */
    public static PageQuery of(Integer page, Integer pageSize, String sort) {
        return new PageQuery(validPage(page), validPageSize(pageSize), validSort(sort));
    }

    /** Sort field with the descending marker stripped. */
    public String sortField() {
        return descending() ? sort.substring(1) : sort;
    }

    public boolean descending() {
        return sort.startsWith("-");
    }

    /** Row offset for a repository query. {@code long} because page × size overflows {@code int}. */
    public long offset() {
        return (long) page * pageSize;
    }

    private static int validPage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw ValidationFailedException.field("page", "must be greater than or equal to 0");
        }
        return page;
    }

    private static int validPageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ValidationFailedException.field("page_size",
                    "must be between 1 and " + MAX_PAGE_SIZE);
        }
        return pageSize;
    }

    private static String validSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        if (!SORT.matcher(sort).matches()) {
            throw ValidationFailedException.field("sort",
                    "must be a snake_case field name, optionally prefixed with '-'");
        }
        return sort;
    }
}
