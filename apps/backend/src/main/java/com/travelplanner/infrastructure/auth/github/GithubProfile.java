package com.travelplanner.infrastructure.auth.github;

import java.util.List;

/**
 * What GitHub reports about the person behind an authorization code: their immutable account id and
 * every address on the account.
 *
 * <p>The addresses arrive <em>unfiltered</em> on purpose. Choosing which one this system may use is
 * the ADR 009 §4 rule — primary and verified only, no {@code @users.noreply.github.com} — and it
 * lives in {@link GithubOAuthIdentityAdapter} where it can be tested exhaustively against a fake
 * {@link GithubApi}, rather than inside the HTTP client where testing it needs a network.
 *
 * @param id GitHub's numeric account id, as a string. The join key, because it never changes: a
 *        user can rename themselves from {@code alice} to {@code bob} between two sign-ins, and the
 *        login name would then resolve to a different person
 */
public record GithubProfile(String id, List<GithubEmail> emails) {

    public GithubProfile {
        emails = emails == null ? List.of() : List.copyOf(emails);
    }

    /** One row of {@code GET /user/emails}. */
    public record GithubEmail(String email, boolean primary, boolean verified) {
    }
}
