package com.travelplanner.infrastructure.auth.github;

import com.travelplanner.config.IdentityProviderProperties;
import com.travelplanner.domain.exception.InvalidCredentialsException;
import com.travelplanner.domain.exception.ProviderUnavailableException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The real GitHub OAuth client — <strong>the only place the client secret is used</strong>
 * (ADR 004: "no Resend or GitHub secret in browser"; PLAN §4.0.5 step 4).
 *
 * <p>Three calls, in order: redeem the code for an access token, read the account, read its
 * addresses. All of it is external HTTP, which is why it lives here and why
 * {@code ExternalIdentityService} performs it before entering any transaction — a provider that
 * takes ten seconds to answer must not hold a pooled database connection for ten seconds
 * ({@code AGENTS.md}).
 *
 * <h2>Failure is classified, not flattened</h2>
 *
 * <ul>
 *   <li>a 4xx or an {@code error} field means the code is unknown, expired, already redeemed, or
 *       was issued to a different OAuth app → {@code invalid_credentials}, the same answer a wrong
 *       password gets;
 *   <li>a 5xx, a timeout, or a connection failure means GitHub is having a bad day →
 *       {@code provider_unavailable}, so the UI says "try again" rather than "report a bug".
 * </ul>
 *
 * <p>The distinction matters beyond wording: an outage silently downgraded to "no such identity"
 * would let a retry create a second account for the same person.
 *
 * <p>The access token is used for two reads and then discarded. It is never stored, never logged,
 * and never becomes a session — task 10's "do not issue Firebase/GitHub tokens as the application
 * session" (ADR 002 issues our own).
 */
public class HttpGithubApi implements GithubApi {

    private static final Logger log = LoggerFactory.getLogger(HttpGithubApi.class);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_ARRAY =
            new ParameterizedTypeReference<>() { };

    private final IdentityProviderProperties.Github properties;
    private final RestClient http;

    public HttpGithubApi(IdentityProviderProperties.Github properties) {
        this.properties = properties;
        this.http = RestClient.builder().requestFactory(timeouts(properties)).build();
        log.info("GitHub OAuth enabled for client id {}", properties.getClientId());
    }

    @Override
    public GithubProfile exchange(String authorizationCode) {
        String accessToken = redeem(authorizationCode);
        return new GithubProfile(accountId(accessToken), addresses(accessToken));
    }

    private String redeem(String authorizationCode) {
        Map<String, Object> response = call(() -> http.post()
                .uri(properties.getTokenUri())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenRequest(authorizationCode))
                .retrieve()
                .body(JSON_OBJECT));

        Object token = response == null ? null : response.get("access_token");
        if (!(token instanceof String value) || value.isBlank()) {
            // GitHub answers a bad code with 200 and `{"error": "bad_verification_code"}`, so the
            // absence of a token is the only reliable signal that the exchange failed.
            log.info("github_code_rejected — the authorization code did not redeem");
            throw new InvalidCredentialsException();
        }
        return value;
    }

    private String accountId(String accessToken) {
        Map<String, Object> account = call(() -> get("/user", accessToken).body(JSON_OBJECT));
        Object id = account == null ? null : account.get("id");
        if (id == null) {
            throw new InvalidCredentialsException();
        }
        // Numeric in the JSON; carried as a string because that is what `provider_subject_id` is.
        return String.valueOf(id);
    }

    private List<GithubProfile.GithubEmail> addresses(String accessToken) {
        List<Map<String, Object>> rows = call(() -> get("/user/emails", accessToken).body(JSON_ARRAY));
        return rows == null ? List.of() : rows.stream().map(HttpGithubApi::toEmail).toList();
    }

    private RestClient.ResponseSpec get(String path, String accessToken) {
        return http.get()
                .uri(properties.getApiBaseUrl() + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .retrieve();
    }

    private static GithubProfile.GithubEmail toEmail(Map<String, Object> row) {
        return new GithubProfile.GithubEmail(
                row.get("email") instanceof String address ? address : null,
                Boolean.TRUE.equals(row.get("primary")),
                Boolean.TRUE.equals(row.get("verified")));
    }

    private MultiValueMap<String, String> tokenRequest(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("code", authorizationCode);
        form.add("redirect_uri", properties.getCallbackUrl());
        return form;
    }

    /**
     * The one place a GitHub failure becomes a registered error code. A 4xx is the caller's
     * credential being wrong; anything else is GitHub's problem and ours to retry.
     */
    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException rejected) {
            if (rejected.getStatusCode().is4xxClientError()) {
                log.info("github_request_rejected — status {}", rejected.getStatusCode().value());
                throw new InvalidCredentialsException();
            }
            throw new ProviderUnavailableException(rejected);
        } catch (RestClientException unreachable) {
            throw new ProviderUnavailableException(unreachable);
        }
    }

    /**
     * Explicit timeouts, because the defaults are "wait forever". A hung provider would otherwise
     * hold a request thread until the container is restarted.
     */
    private static SimpleClientHttpRequestFactory timeouts(IdentityProviderProperties.Github github) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) github.getRequestTimeout().toMillis());
        factory.setReadTimeout((int) github.getRequestTimeout().toMillis());
        return factory;
    }
}
