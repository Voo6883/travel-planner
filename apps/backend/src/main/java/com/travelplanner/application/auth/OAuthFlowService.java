package com.travelplanner.application.auth;

import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.port.OAuthAuthorizationPort;
import com.travelplanner.domain.valueobject.ProviderCredential;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The redirect-based half of external identity: GitHub's authorize round trip (PLAN §4.0.5).
 *
 * <p>It owns the two things a controller must not decide for itself — where the browser goes on the
 * way out, and where it comes back to on the way in — and delegates everything else. The code
 * exchange happens in {@link ExternalIdentityService}, which calls the adapter, which is the only
 * place the client secret exists.
 *
 * <p><b>Every redirect target is built from configuration, never from the request.</b> A callback
 * that echoed a caller-supplied {@code redirect_uri} would be an open redirect attached to a
 * freshly minted session cookie, which is a phishing primitive rather than a convenience.
 */
@Service
@RequiresDatabase
public class OAuthFlowService {

    private final OAuthAuthorizationPort authorization;
    private final ExternalIdentityService identities;
    private final IdentityProviderProperties properties;

    public OAuthFlowService(OAuthAuthorizationPort authorization, ExternalIdentityService identities,
            IdentityProviderProperties properties) {
        this.authorization = authorization;
        this.identities = identities;
        this.properties = properties;
    }

    /** Step 2 of PLAN §4.0.5's GitHub table: the provider's authorize URL, carrying our nonce. */
    public URI authorizationUri(OAuthState state) {
        return authorization.authorizationUri(state.nonce());
    }

    /** UC-A03, UC-A06. The code is exchanged inside the adapter, over HTTPS, server-side. */
    public ExternalSignIn signIn(String authorizationCode) {
        return identities.signIn(authorization.provider(), credential(authorizationCode));
    }

    /** UC-A09 — the same round trip, started by a caller who was already signed in. */
    public void link(UUID callerId, String authorizationCode) {
        identities.link(callerId, authorization.provider(), credential(authorizationCode));
    }

    /** Where a completed round trip lands. */
    public URI appRedirect(boolean linked) {
        return URI.create(properties.getAppBaseUrl() + properties.getSuccessPath()
                + (linked ? "?linked=1" : ""));
    }

    /**
     * Where a failed round trip lands, carrying the registered error code so the frontend renders
     * the same translated message it would for a JSON envelope.
     *
     * @param errorCode a value from {@code errors.yaml}, never a provider message — a provider's
     *        text in a URL is untrusted content on our own origin
     */
    public URI failureRedirect(String errorCode) {
        return URI.create(properties.getAppBaseUrl() + properties.getFailurePath()
                + "?error=" + URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
    }

    /**
     * A missing code means the user declined on GitHub's consent screen, or somebody opened the
     * callback by hand. Both are "this did not authenticate anyone", which is exactly what
     * {@code invalid_credentials} means everywhere else in this API.
     */
    private static ProviderCredential credential(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new InvalidCredentialsException();
        }
        return new ProviderCredential(null, authorizationCode);
    }
}
