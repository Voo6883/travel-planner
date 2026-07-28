package com.travelplanner.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.application.auth.SessionRevocationService;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.infrastructure.persistence.AbstractPostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end local identity, over HTTP, against a real PostgreSQL.
 *
 * <p>This is the suite that proves the task's Definition of Done: the flow works at API level, the
 * cookie attributes are what the ADRs specify, and the failure modes return registered error codes
 * rather than stack traces. Each test names the use case or ADR rule it stands for.
 *
 * <p>Testcontainers, so it lives in {@code src/test/integration} and never runs under
 * {@code ./gradlew test} — that suite must stay Docker-free (PLAN §4.0.2-K).
 */
@AutoConfigureMockMvc
class AuthApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "aisyah@example.com";
    private static final String USERNAME = "aisyah";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private SessionRevocationService revocation;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetAccounts() {
        // ON DELETE CASCADE clears user_identity and refresh_token with it; login_attempt has no
        // foreign key because it counts identifiers that were never registered.
        jdbc.execute("DELETE FROM login_attempt");
        jdbc.execute("DELETE FROM \"user\"");
    }

    // ---------------------------------------------------------------------------------------
    // UC-A01 — registration
    // ---------------------------------------------------------------------------------------

    @Test
    void registersAnAccountThatStartsUnverified() throws Exception {
        register(EMAIL, USERNAME, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));

        User created = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(created.emailVerified()).isFalse();
        assertThat(created.enabled()).isTrue();
        assertThat(created.passwordHash()).isNotNull().doesNotContain(PASSWORD);
        assertThat(created.tokenVersion()).isZero();
    }

    @Test
    void answersADuplicateEmailExactlyAsItAnswersANewOne() throws Exception {
        String first = registeredBody(EMAIL, USERNAME, PASSWORD);
        String duplicate = registeredBody("AISYAH@EXAMPLE.COM", "someone-else", "another-password");

        // ADR 009 §6: identical status and identical body. Anything else enumerates accounts.
        assertThat(duplicate).isEqualTo(first);
        assertThat(countUsers()).isOne();
    }

    @Test
    void answersADuplicateUsernameExactlyAsItAnswersANewOne() throws Exception {
        String first = registeredBody(EMAIL, USERNAME, PASSWORD);
        String duplicate = registeredBody("other@example.com", "AISYAH", "another-password");

        assertThat(duplicate).isEqualTo(first);
        assertThat(countUsers()).isOne();
    }

    @Test
    void rejectsAWeakPasswordAndAUsernameThatLooksLikeAnEmail() throws Exception {
        register(EMAIL, USERNAME, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        // ADR 009 §4 — login accepts an email or a username on one field, so a username shaped
        // like an address would make that field ambiguous.
        register("other@example.com", "victim@example.com", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        assertThat(countUsers()).isZero();
    }

    // ---------------------------------------------------------------------------------------
    // UC-A08 — the verification gate
    // ---------------------------------------------------------------------------------------

    @Test
    void refusesToSignInAnUnverifiedAccountEvenWithTheRightPassword() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());

        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email_not_verified"));
    }

    // ---------------------------------------------------------------------------------------
    // UC-A04 — sign-in
    // ---------------------------------------------------------------------------------------

    @Test
    void signsInByEmailAndByUsername() throws Exception {
        givenVerifiedAccount();

        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(EMAIL));
        // Case-insensitive, matching ux_user_username_lower.
        login("AISYAH", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(USERNAME));
    }

    @Test
    void reportsAWrongPasswordAndAnUnknownAccountIdentically() throws Exception {
        givenVerifiedAccount();

        String wrongPassword = bodyOf(login(EMAIL, "not-the-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials")));
        String unknownAccount = bodyOf(login("nobody@example.com", "not-the-password")
                .andExpect(status().isUnauthorized()));

        assertThat(unknownAccount).isEqualTo(wrongPassword);
    }

    @Test
    void putsNoTokenInTheBodyAndBothTokensInHttpOnlyCookies() throws Exception {
        givenVerifiedAccount();

        MvcResult result = login(EMAIL, PASSWORD).andExpect(status().isOk()).andReturn();

        List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String session = cookieHeader(setCookies, "tp_session");
        String refresh = cookieHeader(setCookies, "tp_refresh");

        // ADR 002 chose a cookie over the Authorization header precisely so that no script can
        // read the token; a token in the JSON body would give that back.
        assertThat(bodyOf(login(EMAIL, PASSWORD))).doesNotContain("eyJ");
        assertThat(session).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/")
                .contains("Max-Age=1800");
        assertThat(refresh).contains("HttpOnly").contains("SameSite=Lax")
                // A fourteen-day credential does not travel on every ordinary API call.
                .contains("Path=/api/v1/auth").contains("Max-Age=1209600");
        // Secure is off under this profile because local development is plain HTTP (ADR 006);
        // application-prod.yml turns it on, and SessionCookieFactoryTest covers both.
        assertThat(session).doesNotContain("Secure");
    }

    // ---------------------------------------------------------------------------------------
    // ADR 009 §6 — lockout
    // ---------------------------------------------------------------------------------------

    @Test
    void locksTheKeyAfterFiveFailuresAndThenRefusesEvenTheCorrectPassword() throws Exception {
        givenVerifiedAccount();

        for (int attempt = 0; attempt < 5; attempt++) {
            login(EMAIL, "not-the-password").andExpect(status().isUnauthorized());
        }

        // The correct password is refused too: the check runs before the comparison, so the
        // lockout cannot be worn down by the attempts it is counting.
        login(EMAIL, PASSWORD)
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("account_locked"))
                .andExpect(jsonPath("$.details.retry_after_seconds").value(900));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_attempt", Integer.class))
                .describedAs("the window is database-backed, not in-process (ADR 009 §6)")
                .isEqualTo(5);
    }

    @Test
    void doesNotLockAnAccountForSomebodyElsesFailuresFromAnotherAddress() throws Exception {
        givenVerifiedAccount();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(loginRequest(EMAIL, "not-the-password").header("X-Forwarded-For", "198.51.100.9"))
                    .andExpect(status().isUnauthorized());
        }

        // The key is the pair (identifier, address). Keying on the identifier alone would let
        // anyone who knows a username lock its owner out of their own account.
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------------------------------
    // UC-A11 — /auth/me
    // ---------------------------------------------------------------------------------------

    @Test
    void returnsTheCallerAndTheirLinkedProviders() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.email_verified").value(true))
                .andExpect(jsonPath("$.linked_providers[0]").value("LOCAL"));
    }

    @Test
    void reportsEmailVerifiedFromCurrentStateRatherThanFromTheToken() throws Exception {
        // ADR 009 §2 and follow-up F-14. The session below was issued while the account was
        // verified; flipping the column has to be visible on the very next request, not thirty
        // minutes later when the access token happens to expire.
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        setEmailVerified(false);

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email_verified").value(false));
    }

    @Test
    void refusesAMissingInvalidOrTamperedSessionWithTheSameEnvelope() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie("tp_session", "not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(new Cookie("tp_session", session.getValue() + "x")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // ADR 009 §1 and §3 — revocation and refresh
    // ---------------------------------------------------------------------------------------

    @Test
    void stopsAcceptingAnAccessTokenOnceTheAccountRevokesItsSessions() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk());

        User account = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        // Through the extension point tasks 09, 10, and 12 will call, rather than the port
        // underneath it — the assertion is about the mechanism those tasks inherit.
        revocation.revokeAllSessions(account.id(), SessionRevocationReason.ADMIN_DISABLED);

        // Still perfectly signed, still unexpired — and rejected, because the token is not the
        // authority. This is the whole point of ADR 009.
        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesTheRefreshTokenAndTreatsAReplayAsTheft() throws Exception {
        givenVerifiedAccount();
        MvcResult signIn = login(EMAIL, PASSWORD).andReturn();
        Cookie firstRefresh = cookieFrom(signIn, "tp_refresh");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(firstRefresh))
                .andExpect(status().isOk())
                .andReturn();
        Cookie secondRefresh = cookieFrom(refreshed, "tp_refresh");
        assertThat(secondRefresh.getValue()).isNotEqualTo(firstRefresh.getValue());

        // Replaying the spent token revokes the whole family — there is no way to tell the thief
        // from the victim, so both are signed out (ADR 009 §3).
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(firstRefresh))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(secondRefresh))
                .andExpect(status().isUnauthorized());
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().tokenVersion()).isOne();
    }

    @Test
    void refusesToRefreshWithoutACookieOrWithAnUnknownToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf())
                        .cookie(new Cookie("tp_refresh", "never-issued")))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // UC-A10 — logout
    // ---------------------------------------------------------------------------------------

    @Test
    void logoutClearsBothCookiesAndInvalidatesTheRefreshTokenServerSide() throws Exception {
        givenVerifiedAccount();
        MvcResult signIn = login(EMAIL, PASSWORD).andReturn();
        Cookie refresh = cookieFrom(signIn, "tp_refresh");

        MvcResult loggedOut = mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(refresh))
                .andExpect(status().isNoContent())
                .andReturn();

        List<String> cleared = loggedOut.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookieHeader(cleared, "tp_session")).contains("Max-Age=0");
        assertThat(cookieHeader(cleared, "tp_refresh")).contains("Max-Age=0").contains("Path=/api/v1/auth");

        // Clearing a cookie is not revocation: without the server-side half, a copied cookie jar
        // would keep refreshing for another fourteen days.
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf()).cookie(refresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutSucceedsWithNoSessionAtAll() throws Exception {
        // A logout that can fail is one users learn to skip.
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void logoutAllTerminatesEverySessionForTheAccount() throws Exception {
        givenVerifiedAccount();
        Cookie phone = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());
        MvcResult laptopSignIn = login(EMAIL, PASSWORD).andReturn();
        Cookie laptop = cookieFrom(laptopSignIn, "tp_session");

        mockMvc.perform(post("/api/v1/auth/logout-all").with(csrf()).cookie(laptop))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").with(csrf())
                        .cookie(cookieFrom(laptopSignIn, "tp_refresh")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutAllRequiresASession() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout-all").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // ADR 006 — CSRF and the public surface
    // ---------------------------------------------------------------------------------------

    @Test
    void rejectsAMutatingRequestThatDoesNotEchoTheCsrfToken() throws Exception {
        // Cookie authentication means the browser attaches credentials to cross-site requests too.
        // Login is protected as well: signing a victim into the attacker's account is a real attack.
        mockMvc.perform(loginRequestWithoutCsrf(EMAIL, PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    // The other half of ADR 006 — that any GET seeds the readable XSRF-TOKEN cookie — is asserted
    // by CsrfCookieFilterTest rather than here. `spring-security-test`'s csrf() post-processor
    // swaps the application's CsrfTokenRepository for an in-request test double on the shared
    // filter chain, so once any test in this class signs in, no test in it can observe a real
    // cookie being written. A unit test on the filter is the honest way to pin that behaviour.

    @Test
    void keepsTheProbesPublicAndEverythingElseAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/ready")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    private void givenVerifiedAccount() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        // Stands in for task 09's verification link. Until the mailer exists there is no other way
        // for a locally registered account to become usable, which is recorded in the task report.
        setEmailVerified(true);
    }

    private void setEmailVerified(boolean verified) {
        User account = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        users.save(new User(account.id(), account.username(), account.email(), account.passwordHash(),
                verified, account.role(), account.enabled(), account.tokenVersion(),
                account.sessionsValidAfter(), account.createdAt(), account.updatedAt()));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String username,
            String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","username":"%s","password":"%s"}""".formatted(email, username, password)));
    }

    private String registeredBody(String email, String username, String password) throws Exception {
        return bodyOf(register(email, username, password).andExpect(status().isAccepted()));
    }

    private org.springframework.test.web.servlet.ResultActions login(String login, String password)
            throws Exception {
        return mockMvc.perform(loginRequest(login, password));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String login, String password) {
        return loginRequestWithoutCsrf(login, password).with(csrf());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            loginRequestWithoutCsrf(String login, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login":"%s","password":"%s"}""".formatted(login, password));
    }

    private int countUsers() {
        return jdbc.queryForObject("SELECT count(*) FROM \"user\"", Integer.class);
    }

    private static String bodyOf(org.springframework.test.web.servlet.ResultActions actions)
            throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private static Cookie sessionCookieFrom(MvcResult result) {
        return cookieFrom(result, "tp_session");
    }

    private static Cookie cookieFrom(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).describedAs("cookie %s", name).isNotNull();
        return cookie;
    }

    /** The raw header, so attributes a servlet {@code Cookie} does not model can be asserted. */
    private static String cookieHeader(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }
}
