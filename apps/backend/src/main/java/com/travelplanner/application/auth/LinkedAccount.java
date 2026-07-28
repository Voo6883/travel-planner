package com.travelplanner.application.auth;

import com.travelplanner.domain.model.User;
import java.util.Objects;

/**
 * Which of the three things an external sign-in turned out to be (PLAN §4.0.5's Gmail table).
 *
 * <p>The two flags are not derived state a caller could recompute — by the time the session is
 * issued, a created account and a returning one look identical. They are the record of what
 * {@link AccountLinkingService} decided, and they carry two obligations with them: the welcome mail
 * is sent exactly when {@code newUser} is true (PLAN §4.0.10), and the UI's "your Google account is
 * now connected" notice appears exactly when {@code providerLinked} is true (UC-A09).
 *
 * <p>They are mutually exclusive by construction — the three factory methods are the only way to
 * build one — because "a brand-new account that also linked to an existing account" is not a state
 * that exists.
 */
public record LinkedAccount(User user, boolean newUser, boolean providerLinked) {

    public LinkedAccount {
        Objects.requireNonNull(user, "user");
    }

    /** The provider identity was already attached to this account (UC-A05, UC-A06). */
    public static LinkedAccount returning(User user) {
        return new LinkedAccount(user, false, false);
    }

    /** First sign-in with this provider and no account held the address (UC-A02, UC-A03). */
    public static LinkedAccount created(User user) {
        return new LinkedAccount(user, true, false);
    }

    /** An existing, verified account gained a new sign-in method (UC-A09). */
    public static LinkedAccount linked(User user) {
        return new LinkedAccount(user, false, true);
    }
}
