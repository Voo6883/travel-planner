package com.travelplanner.domain.port;

import com.travelplanner.domain.enums.AuthProvider;
import java.net.URI;

/**
 * Builds the provider's authorization URL — the first leg of a redirect-based OAuth flow
 * (PLAN §4.0.5, GitHub step 2).
 *
 * <p>Separate from {@link IdentityProviderPort} because the two halves of an OAuth round trip are
 * genuinely different operations: this one is pure string assembly with no network call and no
 * credential to verify, while {@code authenticate} exchanges a code over HTTPS. Merging them would
 * put a method on every identity adapter that only redirect-based providers can implement.
 *
 * <p>It exists at all so that {@code client_id}, {@code scope}, and the callback URL stay in
 * {@code infrastructure/auth/}: the application layer needs a URL to send the browser to, not the
 * credentials used to build one.
 */
public interface OAuthAuthorizationPort {

    /** Which provider this flow speaks for. */
    AuthProvider provider();

    /**
     * @param state the single-use CSRF nonce the callback will be required to echo. It is passed in
     *        rather than generated here so that one value is issued, sent to the browser as a
     *        cookie, and put in this URL — an adapter that minted its own would have nothing to
     *        compare against on the way back
     */
    URI authorizationUri(String state);
}
