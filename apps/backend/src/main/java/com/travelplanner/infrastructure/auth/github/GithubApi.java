package com.travelplanner.infrastructure.auth.github;

/**
 * The GitHub side of the OAuth exchange: authorization code in, account out.
 *
 * <p>A seam with two implementations, chosen by {@code IdentityProviderConfig} — the real HTTP
 * client, and a development one that needs no OAuth app. Task 10 forbids relying on a live provider
 * in CI, and this is where that is arranged.
 *
 * <p>Everything behind this interface is network: the token exchange, {@code GET /user}, and
 * {@code GET /user/emails}. Nothing behind it is a rule. That separation is what keeps the code
 * exchange out of any transaction ({@code AGENTS.md}) and keeps the email-selection rule testable
 * without one.
 */
public interface GithubApi {

    /**
     * @throws com.travelplanner.domain.exception.InvalidCredentialsException when the code is
     *         unknown, expired, already redeemed, or was issued to another OAuth app
     * @throws com.travelplanner.domain.exception.ProviderUnavailableException when GitHub cannot be
     *         reached or answers with a fault
     */
    GithubProfile exchange(String authorizationCode);
}
