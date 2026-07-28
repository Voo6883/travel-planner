package com.travelplanner.application.admin;

import com.travelplanner.domain.model.User;
import java.util.List;

/**
 * One page of accounts for {@code GET /admin/users} (UC-A15).
 *
 * <p>{@code total} travels with the items rather than being counted by the caller: the page and the
 * count have to come from the same read, or a list rendered while somebody registers shows twenty
 * rows and claims there are nineteen.
 */
public record AdminUserPage(List<User> items, long total) {

    public AdminUserPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
