package com.travelplanner.infrastructure.auth.github;

import com.travelplanner.domain.exception.InvalidCredentialsException;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * The development stand-in for {@link HttpGithubApi} (§4.0.7 stub-adapter rule).
 *
 * <p>Contacts nothing. A "code" is read as {@code dev:<id>:<email>}, which is enough to walk the
 * whole GitHub flow — start, redirect, callback, session — with no OAuth app registered, so
 * {@code docker compose up} and CI both work with no GitHub credentials. That is task 10's "do not
 * rely on a live provider in CI" as a default rather than as a note in a README.
 *
 * <p>Fenced exactly like {@code DevFirebaseTokenVerifier}: only constructed when no client id and
 * secret are configured, refuses to exist under the {@code prod} profile, and warns on every use.
 * A production deployment that hands out sessions for a made-up code is worse than one that does
 * not start.
 *
 * <p>The address it reports is always primary and verified, so the ADR 009 §4 selection rule in
 * {@link GithubOAuthIdentityAdapter} still runs — this class fakes the network, never the rule.
 */
public class DevGithubApi implements GithubApi {

    private static final Logger log = LoggerFactory.getLogger(DevGithubApi.class);

    private static final String PREFIX = "dev:";

    private DevGithubApi() {
    }

    /**
     * @throws IllegalStateException under {@code prod} — see the class javadoc
     */
    public static DevGithubApi create(Environment environment) {
        if (environment.matchesProfiles("prod")) {
            throw new IllegalStateException("GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET must be set "
                    + "in prod — refusing to accept GitHub sign-ins without contacting GitHub");
        }
        log.warn("GITHUB_CLIENT_ID is unset — GitHub sign-in accepts development codes of the form "
                + "dev:<id>:<email> without contacting GitHub. Development only.");
        return new DevGithubApi();
    }

    @Override
    public GithubProfile exchange(String authorizationCode) {
        String code = authorizationCode == null ? "" : authorizationCode.trim();
        if (!code.toLowerCase(Locale.ROOT).startsWith(PREFIX)) {
            throw new InvalidCredentialsException();
        }
        String[] parts = code.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new InvalidCredentialsException();
        }
        log.warn("Accepting a DEVELOPMENT GitHub authorization code for account {}", parts[0]);
        return new GithubProfile(parts[0],
                List.of(new GithubProfile.GithubEmail(parts[1], true, true)));
    }
}
