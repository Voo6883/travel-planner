package com.travelplanner.application.admin;

import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.model.UserIdentity;
import java.util.List;

/**
 * One account as an administrator sees it (UC-A15) — the account row plus the sign-in methods
 * attached to it.
 *
 * <p>Deliberately <em>not</em> {@code CurrentUserView}. That record answers "who am I" and is
 * shaped by what the signed-in user needs; this one answers "what state is this account in" and
 * carries {@code enabled}, {@code closed}, and {@code hasLocalPassword}, none of which the profile
 * endpoint publishes. Reusing one record for both would mean every field either surface needs
 * becomes a field the other publishes too.
 *
 * <p><b>What is absent is the point.</b> No password hash, no token version, no session state, and
 * nothing about trips, chats, or bookings. PLAN §4.0.6 confines administration to accounts, and a
 * field added here is a field published to every administrator.
 */
public record AdminUserView(User account, List<String> linkedProviders) {

    public AdminUserView {
        linkedProviders = linkedProviders == null ? List.of() : List.copyOf(linkedProviders);
    }

    public static AdminUserView of(User account, List<UserIdentity> identities) {
        return new AdminUserView(account, identities.stream()
                .map(UserIdentity::provider)
                .map(AuthProvider::name)
                .sorted()
                .toList());
    }

    /**
     * ADR 009 §4's marker for an account that signs in only through a provider. The single bit an
     * administrator needs in order to know whether "reset password" applies at all — and the hash
     * itself is never exposed in any form.
     */
    public boolean hasLocalPassword() {
        return !account.isOAuthOnly();
    }
}
