package com.travelplanner.infrastructure.auth.github;

import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.domain.enums.AuthProvider;
import com.travelplanner.domain.port.IdentityProviderPort;
import com.travelplanner.domain.port.OAuthAuthorizationPort;
import com.travelplanner.domain.valueobject.IdentityClaims;
import com.travelplanner.domain.valueobject.ProviderCredential;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The {@code GITHUB} identity adapter (PLAN §4.0.5).
 *
 * <p>Implements both halves of a redirect flow: {@link OAuthAuthorizationPort} builds the URL the
 * browser is sent to, and {@link IdentityProviderPort} redeems the code it comes back with. One
 * bean, because the {@code client_id} in the first and the {@code client_secret} in the second are
 * two halves of one credential and splitting them across classes would only mean configuring them
 * twice.
 *
 * <h2>The email rule (ADR 009 §4)</h2>
 *
 * <p>Only the address that is <strong>primary and verified</strong> is used, and
 * {@code @users.noreply.github.com} is never used at all. Every part of that is a refusal to accept
 * something that looks like evidence and is not:
 *
 * <ul>
 *   <li><b>unverified</b> — a string the user typed into GitHub and nobody checked. Accepting it
 *       lets anyone claim any address, which is exactly the door {@code /auth/register}'s
 *       verification mail closes;
 *   <li><b>non-primary</b> — a secondary address is a real address, but it is not the one the
 *       account is identified by, and picking arbitrarily between several makes which account a
 *       user lands in depend on GitHub's array ordering;
 *   <li><b>{@code noreply} alias</b> — GitHub's privacy placeholder. It routes nowhere, so no
 *       verification, password reset, or trip notification could ever reach the person, and it is
 *       derived from a username that can be changed.
 * </ul>
 *
 * <p>When nothing qualifies the claims carry a {@code null} address rather than a fabricated one.
 * That is a usable answer: an identity that is already linked signs in fine without one, and only
 * <em>creating</em> an account needs an address — {@code AccountLinkingService} raises
 * {@code provider_email_unavailable} there.
 *
 * <p>The subject is GitHub's numeric account id, never the login name. Logins are renameable and
 * reusable; the id is not.
 */
@Component
public class GithubOAuthIdentityAdapter implements IdentityProviderPort, OAuthAuthorizationPort {

    private static final Logger log = LoggerFactory.getLogger(GithubOAuthIdentityAdapter.class);

    /** GitHub's privacy alias domain. Deliverable to nobody, and derived from a mutable username. */
    static final String NOREPLY_SUFFIX = "@users.noreply.github.com";

    private final GithubApi api;
    private final IdentityProviderProperties.Github properties;

    public GithubOAuthIdentityAdapter(GithubApi api, IdentityProviderProperties properties) {
        this.api = api;
        this.properties = properties.getGithub();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GITHUB;
    }

    @Override
    public URI authorizationUri(String state) {
        return UriComponentsBuilder.fromUriString(properties.getAuthorizeUri())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getCallbackUrl())
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                // Forces the account chooser rather than silently reusing whichever GitHub session
                // the browser happens to hold — the same reason PLAN §4.0.5 sets `prompt` on Google.
                .queryParam("allow_signup", "false")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    @Override
    public IdentityClaims authenticate(ProviderCredential credential) {
        GithubProfile profile = api.exchange(credential.secret());
        Optional<String> email = usableEmail(profile);
        if (email.isEmpty()) {
            log.info("github_email_unavailable — no primary, verified, routable address on the account");
        }
        // `emailVerified` mirrors whether an address survived the filter: everything that reaches
        // here verified, and nothing else is reported at all.
        return new IdentityClaims(AuthProvider.GITHUB, profile.id(), email.orElse(null),
                email.isPresent());
    }

    private static Optional<String> usableEmail(GithubProfile profile) {
        return profile.emails().stream()
                .filter(GithubProfile.GithubEmail::primary)
                .filter(GithubProfile.GithubEmail::verified)
                .map(GithubProfile.GithubEmail::email)
                .filter(address -> address != null && !address.isBlank())
                .filter(address -> !address.toLowerCase(java.util.Locale.ROOT).endsWith(NOREPLY_SUFFIX))
                .findFirst();
    }
}
