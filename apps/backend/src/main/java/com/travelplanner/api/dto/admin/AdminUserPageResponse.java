package com.travelplanner.api.dto.admin;

import com.travelplanner.api.dto.page.PageMetadata;
import com.travelplanner.application.page.PageQuery;
import com.travelplanner.application.admin.AdminUserPage;
import java.util.List;

/**
 * The body of {@code GET /admin/users} (UC-A15) — the shared pagination envelope plus this
 * endpoint's own typed item array.
 *
 * <p>Each list endpoint declares its own response record rather than sharing a generic
 * {@code PageResponse<T>}, for the reason {@link PageMetadata} spells out: OpenAPI 3.0 has no
 * generics, so a shared wrapper would degrade to {@code items: object} in the contract and erase the
 * element type in the generated client — the exact drift codegen exists to prevent.
 */
public record AdminUserPageResponse(int page, int pageSize, long total,
        List<AdminUserSummaryResponse> items) {

    public static AdminUserPageResponse from(AdminUserPage page, PageQuery query) {
        PageMetadata metadata = PageMetadata.of(query, page.total());
        return new AdminUserPageResponse(metadata.page(), metadata.pageSize(), metadata.total(),
                page.items().stream().map(AdminUserSummaryResponse::from).toList());
    }
}
