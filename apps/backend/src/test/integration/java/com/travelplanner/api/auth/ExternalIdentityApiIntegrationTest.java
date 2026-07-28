package com.travelplanner.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.api.auth.ExternalIdentityTestProviders.FakeFirebaseTokenVerifier;
import com.travelplanner.api.auth.ExternalIdentityTestProviders.FakeGithubApi;
import com.travelplanner.domain.exception.ProviderUnavailableException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.MailMessage;
import com.travelplanner.infrastructure.auth.firebase.FirebaseIdToken;
import com.travelplanner.infrastructure.auth.github.GithubProfile;
import com.travelplanner.infrastructure.auth.github.GithubProfile.GithubEmail;
import com.travelplanner.infrastructure.mail.StubMailerAdapter;
import com.travelplanner.infrastructure.persistence.AbstractPostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * External identity end to end, over HTTP, against a real PostgreSQL — the suite that proves this
 * task's Definition of Done.
 *
 * <p>Only the two provider network calls are faked ({@link ExternalIdentityTestProviders}); every
 * rule runs for real, including the ADR 009 §4 assertions that keep an attacker out. The headline
 * test is {@link #refusesToLinkAVictimsGoogleIdentityIntoAnAttackersAccount} — the pre-hijack
 * takeover, at API level, against the actual database.
 *
 * <p>Testcontainers, so it lives in {@code src/test/integration} and never runs under
 * {@code ./gradlew test} (PLAN §4.0.2-K).
 */
@AutoConfigureMockMvc
@Import(ExternalIdentityTestProviders.class)
@TestPropertySource(properties = {
    // A configured project id means FirebaseIdentityAdapter's `aud` and issuer assertions are the
    // real ones here rather than a development shortcut.
    "travelplanner.identity.firebase.project-id=travel-planner-it",
    "travelplanner.identity.app-base-url=https://app.example.test",
    "travelplanner.identity.github.client-id=it-client",
    "travelplanner.identity.github.client-secret=it-secret",
})
class ExternalIdentityApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PROJECT_ID = "travel-planner-it";
    private static final String ISSUER = "https://securetoken.google.com/" + PROJECT_ID;
    private static final String GOOGLE_UID = "google-uid-1";
    private static final String EMAIL = "aisyah@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    /**
     * The fake verifier keys on the string, so its content is arbitrary — but it still has to clear
     * the contract's 20-character floor, because {@code FirebaseAuthRequest} validates before the
     * adapter is ever reached and a two-character "token" would be rejected as malformed input.
     */
    private static final String ID_TOKEN = "firebase-id-token-primary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private StubMailerAdapter mailer;

    @Autowired
    private FakeFirebaseTokenVerifier firebase;

    @Autowired
    private FakeGithubApi github;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("DELETE FROM mail_rate_limit");
        jdbc.execute("DELETE FROM login_attempt");
        jdbc.execute("DELETE FROM \"user\"");
        mailer.clear();
        firebase.reset();
        github.reset();
    }

    // ---------------------------------------------------------------------------------------
    // UC-A02 — first login through Google
    // ---------------------------------------------------------------------------------------

    @Test
    void firstFirebaseSignInCreatesAVerifiedAccountAndSendsOneWelcomeMail() throws Exception {
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));

        MvcResult result = firebaseSignIn(ID_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(true))
                .andExpect(jsonPath("$.provider_linked").value(false))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.email_verified").value(true))
                .andExpect(jsonPath("$.user.linked_providers[0]").value("FIREBASE_GOOGLE"))
                .andReturn();

        // The session is ours, not Firebase's (task 10 "do not issue provider tokens as the
        // application session"), and neither token is in the body (ADR 002).
        assertThat(result.getResponse().getCookie("tp_session")).isNotNull();
        assertThat(result.getResponse().getCookie("tp_refresh")).isNotNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ID_TOKEN);

        User created = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(created.emailVerified()).isTrue();
        assertThat(created.isOAuthOnly()).isTrue();

        // PLAN §4.0.10: welcome on `is_new_user` only, and no verification mail — Google confirmed
        // the address already.
        assertThat(mailer.outbox()).extracting(MailMessage::subject)
                .containsExactly("Welcome to Travel Planner");
    }

    @Test
    void returningFirebaseSignInIssuesASessionAndCreatesNothing() throws Exception {
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));
        firebaseSignIn(ID_TOKEN).andExpect(status().isOk());
        mailer.clear();

        firebaseSignIn(ID_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.provider_linked").value(false));

        assertThat(countUsers()).isOne();
        assertThat(mailer.outbox()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // ADR 009 §4 — linking, and the takeover it prevents
    // ---------------------------------------------------------------------------------------

    @Test
    void autoLinksIntoAVerifiedLocalAccount() throws Exception {
        givenVerifiedLocalAccount();
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));

        firebaseSignIn(ID_TOKEN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_new_user").value(false))
                .andExpect(jsonPath("$.provider_linked").value(true))
                .andExpect(jsonPath("$.user.linked_providers",
                        org.hamcrest.Matchers.containsInAnyOrder("FIREBASE_GOOGLE", "LOCAL")));

        assertThat(countUsers()).describedAs("one person, one account").isOne();
    }

    @Test
    void refusesToLinkAVictimsGoogleIdentityIntoAnAttackersAccount() throws Exception {
        // The documented pre-hijack takeover, end to end. An attacker registers locally with an
        // address they do not own; the verification mail goes to the victim, so this row is
        // unverified. Nothing else about it looks unusual.
        register(EMAIL, "attacker", PASSWORD).andExpect(status().isAccepted());
        mailer.clear();
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));

        // The victim signs in with Google. Under email-equality linking this is the moment their
        // identity is attached to the attacker's row.
        firebaseSignIn(ID_TOKEN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("provider_link_required"))
                .andExpect(jsonPath("$.details.provider").value("FIREBASE_GOOGLE"));

        // Nothing was written: no Google identity on the attacker's account, and no session issued.
        assertThat(identityCount("FIREBASE_GOOGLE")).isZero();
        assertThat(countUsers()).isOne();
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().emailVerified()).isFalse();
        assertThat(mailer.outbox()).isEmpty();
    }

    @Test
    void theRefusedLinkSucceedsOnceTheAccountOwnerConfirmsItWhileSignedIn() throws Exception {
        // The recovery ADR 009 §4 prescribes: holding a session for the account is the proof a
        // matching address is not.
        givenVerifiedLocalAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());
        firebase.register(ID_TOKEN, googleToken("a-different-address@example.com", true));

        mockMvc.perform(post("/api/v1/auth/providers/FIREBASE_GOOGLE").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + ID_TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(jsonPath("$.linked_providers",
                        org.hamcrest.Matchers.containsInAnyOrder("FIREBASE_GOOGLE", "LOCAL")));
    }

    @Test
    void refusesAnExplicitLinkOfAnIdentityAnotherAccountAlreadyOwns() throws Exception {
        // One external identity, one user — the conflict case.
        firebase.register(ID_TOKEN, googleToken("google-owner@example.com", true));
        firebaseSignIn(ID_TOKEN).andExpect(status().isOk());

        givenVerifiedLocalAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(post("/api/v1/auth/providers/FIREBASE_GOOGLE").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"" + ID_TOKEN + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("identity_already_linked"));

        assertThat(identityCount("FIREBASE_GOOGLE")).isOne();
    }

    @Test
    void refusesToLinkWithoutASession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/providers/FIREBASE_GOOGLE").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"an-id-token-value\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // Invalid token (ADR 009 §4 — the two Firebase assertions)
    // ---------------------------------------------------------------------------------------

    @Test
    void rejectsAnUnknownTokenAndOneMintedForAnotherFirebaseProject() throws Exception {
        firebaseSignIn("firebase-id-token-never-issued")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_firebase_token"));

        firebase.register("firebase-id-token-wrong-project", new FirebaseIdToken("somebody-elses-project", ISSUER,
                GOOGLE_UID, EMAIL, true, "google.com"));
        firebaseSignIn("firebase-id-token-wrong-project")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_firebase_token"));

        assertThat(countUsers()).isZero();
    }

    @Test
    void rejectsAFirebaseEmailPasswordSignIn() throws Exception {
        // Enabling Email/Password in the Firebase console is one toggle. Without this assertion it
        // would be an unvetted registration path into this system (ADR 009 §4).
        firebase.register("firebase-id-token-password-signin", new FirebaseIdToken(PROJECT_ID, ISSUER, GOOGLE_UID,
                EMAIL, true, "password"));

        firebaseSignIn("firebase-id-token-password-signin")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_firebase_token"));

        assertThat(countUsers()).isZero();
    }

    @Test
    void refusesAnUnverifiedGoogleAddress() throws Exception {
        firebase.register(ID_TOKEN, googleToken(EMAIL, false));

        firebaseSignIn(ID_TOKEN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("firebase_email_not_verified"));
    }

    // ---------------------------------------------------------------------------------------
    // Provider outage
    // ---------------------------------------------------------------------------------------

    @Test
    void reportsAProviderOutageAsRetryableRatherThanAsAServerFault() throws Exception {
        firebase.failure = new ProviderUnavailableException(new IllegalStateException("jwks down"));

        firebaseSignIn(ID_TOKEN)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("provider_unavailable"));

        assertThat(countUsers()).describedAs("an outage creates nothing").isZero();
    }

    // ---------------------------------------------------------------------------------------
    // UC-A03, UC-A06 — the GitHub round trip
    // ---------------------------------------------------------------------------------------

    @Test
    void githubStartRedirectsToGithubWithAStateCookieAndNoSecret() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/github/start"))
                .andExpect(status().isFound())
                .andReturn();

        // Asserted with startsWith rather than redirectedUrlPattern: Ant patterns treat `?` as a
        // single-character wildcard, so no pattern can match a URL that has a query string.
        assertThat(result.getResponse().getRedirectedUrl())
                .startsWith("https://github.com/login/oauth/authorize?");

        Cookie state = result.getResponse().getCookie("tp_oauth_state");
        assertThat(state).isNotNull();
        assertThat(state.isHttpOnly()).describedAs("no script may read or forge it").isTrue();
        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("it-secret");
        // The cookie carries the nonce; the URL echoes it. Neither alone is enough at the callback.
        assertThat(result.getResponse().getRedirectedUrl())
                .contains("state=" + state.getValue().substring(2));
    }

    @Test
    void githubCallbackSignsInAndSetsOurOwnSessionCookies() throws Exception {
        github.register("code-1", profile("42", new GithubEmail(EMAIL, true, true)));

        MvcResult started = mockMvc.perform(get("/api/v1/auth/oauth/github/start")).andReturn();
        Cookie state = started.getResponse().getCookie("tp_oauth_state");

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "code-1")
                        .param("state", state.getValue().substring(2))
                        .cookie(state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://app.example.test/trips"))
                .andReturn();

        assertThat(callback.getResponse().getCookie("tp_session")).isNotNull();
        // Single-use: the state cookie is cleared whether or not it matched.
        assertThat(callback.getResponse().getCookie("tp_oauth_state").getMaxAge()).isZero();
        assertThat(users.findByEmailIgnoreCase(EMAIL)).isPresent();
    }

    @Test
    void githubCallbackRefusesAMismatchedOrMissingState() throws Exception {
        github.register("code-1", profile("42", new GithubEmail(EMAIL, true, true)));
        MvcResult started = mockMvc.perform(get("/api/v1/auth/oauth/github/start")).andReturn();
        Cookie state = started.getResponse().getCookie("tp_oauth_state");

        // Without the state check the callback signs a browser into whatever account an attacker's
        // authorization code names — login CSRF.
        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "code-1").param("state", "not-the-issued-nonce")
                        .cookie(state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location",
                        "https://app.example.test/sign-in?error=invalid_oauth_state"));

        // No cookie at all is the same answer.
        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "code-1").param("state", "anything"))
                .andExpect(header().string("Location",
                        "https://app.example.test/sign-in?error=invalid_oauth_state"));

        assertThat(countUsers()).isZero();
    }

    @Test
    void githubCallbackRefusesAnAccountWithNoPrimaryVerifiedAddress() throws Exception {
        // Private or unverified addresses only: nothing this system may put on a new account.
        github.register("code-1", profile("42",
                new GithubEmail("42+aisyah@users.noreply.github.com", true, true),
                new GithubEmail("secondary@example.com", false, true)));

        MvcResult started = mockMvc.perform(get("/api/v1/auth/oauth/github/start")).andReturn();
        Cookie state = started.getResponse().getCookie("tp_oauth_state");

        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .param("code", "code-1").param("state", state.getValue().substring(2))
                        .cookie(state))
                .andExpect(header().string("Location",
                        "https://app.example.test/sign-in?error=provider_email_unavailable"));

        assertThat(countUsers()).isZero();
    }

    @Test
    void githubLinkModeDemandsALiveSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/github/start").param("mode", "link"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    // ---------------------------------------------------------------------------------------
    // ADR 009 §4 — unlink
    // ---------------------------------------------------------------------------------------

    @Test
    void unlinkRefusesToStrandAnAccountWithNoOtherWayIn() throws Exception {
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));
        Cookie session = sessionCookieFrom(firebaseSignIn(ID_TOKEN).andReturn());

        mockMvc.perform(delete("/api/v1/auth/providers/FIREBASE_GOOGLE").with(csrf()).cookie(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("last_sign_in_method"));

        // Still linked, still usable.
        assertThat(identityCount("FIREBASE_GOOGLE")).isOne();
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void unlinkRemovesTheProviderAndTerminatesEverySession() throws Exception {
        givenVerifiedLocalAccount();
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));
        firebaseSignIn(ID_TOKEN).andExpect(status().isOk());
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(delete("/api/v1/auth/providers/FIREBASE_GOOGLE").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        assertThat(identityCount("FIREBASE_GOOGLE")).isZero();
        // ADR 009 §1: the caller's own perfectly-signed token stops working on its next request.
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().tokenVersion()).isOne();
    }

    @Test
    void unlinkReportsAProviderThatWasNeverLinkedAsNotFound() throws Exception {
        givenVerifiedLocalAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(delete("/api/v1/auth/providers/GITHUB").with(csrf()).cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void unlinkRefusesLocalBecauseItWouldNotRemoveThePassword() throws Exception {
        givenVerifiedLocalAccount();
        firebase.register(ID_TOKEN, googleToken(EMAIL, true));
        firebaseSignIn(ID_TOKEN).andExpect(status().isOk());
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(delete("/api/v1/auth/providers/LOCAL").with(csrf()).cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    private static FirebaseIdToken googleToken(String email, boolean emailVerified) {
        return new FirebaseIdToken(PROJECT_ID, ISSUER, GOOGLE_UID, email, emailVerified, "google.com");
    }

    private static GithubProfile profile(String id, GithubEmail... emails) {
        return new GithubProfile(id, List.of(emails));
    }

    private void givenVerifiedLocalAccount() throws Exception {
        register(EMAIL, "aisyah", PASSWORD).andExpect(status().isAccepted());
        User account = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        users.save(account.withEmailVerified(true, account.updatedAt()));
        mailer.clear();
    }

    private ResultActions firebaseSignIn(String idToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/firebase").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id_token\":\"%s\"}".formatted(idToken)));
    }

    private ResultActions register(String email, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","username":"%s","password":"%s"}"""
                        .formatted(email, username, password)));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login":"%s","password":"%s"}""".formatted(login, password)));
    }

    private int countUsers() {
        return jdbc.queryForObject("SELECT count(*) FROM \"user\"", Integer.class);
    }

    private int identityCount(String provider) {
        return jdbc.queryForObject("SELECT count(*) FROM user_identity WHERE provider = ?",
                Integer.class, provider);
    }

    private static Cookie sessionCookieFrom(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("tp_session");
        assertThat(cookie).describedAs("tp_session").isNotNull();
        return cookie;
    }
}
