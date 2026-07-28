package com.travelplanner.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.domain.valueobject.MailMessage;
import com.travelplanner.infrastructure.mail.StubMailerAdapter;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * The account lifecycle end to end, over HTTP, against a real PostgreSQL and the stub mailer.
 *
 * <p>This is the suite that proves task 09's Definition of Done: every lifecycle API works with the
 * stub provider, the mailed links are real and redeemable, revocation actually terminates sessions,
 * and a deleted account keeps nothing that identifies a person.
 *
 * <p>Testcontainers, so it lives in {@code src/test/integration} and never runs under
 * {@code ./gradlew test} — that suite must stay Docker-free (PLAN §4.0.2-K).
 */
@AutoConfigureMockMvc
class AccountLifecycleApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String EMAIL = "aisyah@example.com";
    private static final String USERNAME = "aisyah";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "a-brand-new-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private StubMailerAdapter mailer;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetAccountsAndOutbox() {
        // account_token and refresh_token cascade from "user"; mail_rate_limit has no foreign key
        // because it counts addresses that were never registered.
        jdbc.execute("DELETE FROM mail_rate_limit");
        jdbc.execute("DELETE FROM login_attempt");
        jdbc.execute("DELETE FROM \"user\"");
        mailer.clear();
    }

    // ---------------------------------------------------------------------------------------
    // UC-A01 + UC-N01/N02 — registration now mails, whatever happened
    // ---------------------------------------------------------------------------------------

    @Test
    void mailsAWelcomeAndAVerificationLinkOnASuccessfulSignUp() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());

        assertThat(mailer.outbox()).extracting(MailMessage::subject)
                .containsExactly("Confirm your email address", "Welcome to Travel Planner");
    }

    @Test
    void answersATakenUsernameUniformlyButStillTellsTheSubmittedAddressWhy() throws Exception {
        String first = bodyOf(register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted()));
        mailer.clear();

        String collision = bodyOf(register("someone-else@example.com", "AISYAH", PASSWORD)
                .andExpect(status().isAccepted()));

        // ADR 009 §6 — the HTTP response is byte-identical.
        assertThat(collision).isEqualTo(first);
        // …and the mailbox carries the answer, which is what makes UC-A01 usable from a UI. Before
        // this task the user was told "check your email" and nothing ever arrived.
        assertThat(mailer.outbox()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("someone-else@example.com");
            assertThat(message.textBody()).contains("That username is taken");
        });
        assertThat(countUsers()).isOne();
    }

    @Test
    void tellsAnExistingAddressToSignInRatherThanCreatingASecondAccount() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        mailer.clear();

        register("AISYAH@EXAMPLE.COM", "someone-else", PASSWORD).andExpect(status().isAccepted());

        assertThat(mailer.outbox()).singleElement().satisfies(message ->
                assertThat(message.textBody()).contains("You already have an account"));
        assertThat(countUsers()).isOne();
    }

    // ---------------------------------------------------------------------------------------
    // UC-A08 / UC-A13 — verify email
    // ---------------------------------------------------------------------------------------

    @Test
    void verificationLinkOpensTheLoginGateThatBlockedTheAccount() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        login(EMAIL, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email_not_verified"));

        confirmVerification(tokenFromMailContaining("Confirm your email"))
                .andExpect(status().isNoContent());

        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().emailVerified()).isTrue();
        login(EMAIL, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void refusesAReusedUnknownOrExpiredVerificationTokenWithOneCode() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        String token = tokenFromMailContaining("Confirm your email");
        confirmVerification(token).andExpect(status().isNoContent());

        String reused = bodyOf(confirmVerification(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_token")));
        String unknown = bodyOf(confirmVerification("never-issued-token")
                .andExpect(status().isBadRequest()));

        // Telling "already used" from "never existed" confirms a token was once issued to a real
        // account, which is a fact about somebody else's mailbox.
        assertThat(unknown).isEqualTo(reused);
    }

    @Test
    void resendMailsAFreshLinkAndInvalidatesTheOldOne() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        String original = tokenFromMailContaining("Confirm your email");
        mailer.clear();

        resendVerification(EMAIL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        String replacement = tokenFromMailContaining("Confirm your email");
        assertThat(replacement).isNotEqualTo(original);
        // Three "resend" clicks must not leave three working links in one mailbox.
        confirmVerification(original).andExpect(status().isBadRequest());
        confirmVerification(replacement).andExpect(status().isNoContent());
    }

    @Test
    void resendAnswersAnUnregisteredAddressExactlyAsARegisteredOneAndMailsNothing() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        mailer.clear();

        String registered = bodyOf(resendVerification(EMAIL).andExpect(status().isAccepted()));
        mailer.clear();
        String unregistered = bodyOf(resendVerification("nobody@example.com")
                .andExpect(status().isAccepted()));

        assertThat(unregistered).isEqualTo(registered);
        // No mail to the stranger: "you have no account here", sent on request to any address,
        // would make this endpoint a way to mail people who never used the product.
        assertThat(mailer.outbox()).isEmpty();
    }

    @Test
    void limitsResendsPerAddressAndSaysHowLongToWait() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());

        for (int attempt = 0; attempt < 3; attempt++) {
            resendVerification(EMAIL).andExpect(status().isAccepted());
        }

        resendVerification(EMAIL)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"))
                .andExpect(jsonPath("$.details.retry_after_seconds").value(3600));

        // Database-backed, not in-process: an in-memory counter would reset on deploy and count
        // separately on every instance (ADR 009 §6).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_rate_limit", Integer.class))
                .isEqualTo(6);
    }

    @Test
    void storesNoPlaintextAddressInTheRateLimitTable() throws Exception {
        resendVerification(EMAIL).andExpect(status().isAccepted());

        List<String> subjects = jdbc.queryForList(
                "SELECT subject_hash FROM mail_rate_limit", String.class);
        assertThat(subjects).isNotEmpty().allSatisfy(hash ->
                assertThat(hash).hasSize(64).doesNotContain("aisyah").doesNotContain("@"));
    }

    // ---------------------------------------------------------------------------------------
    // UC-A07 — forgot / reset
    // ---------------------------------------------------------------------------------------

    @Test
    void resetLinkReplacesThePasswordAndTerminatesEverySession() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk());
        mailer.clear();

        forgotPassword(EMAIL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        resetPassword(tokenFromMailContaining("Reset your Travel Planner password"), NEW_PASSWORD)
                .andExpect(status().isNoContent());

        // ADR 009 §1 — the session that existed before the reset is, in the case this flow exists
        // for, the attacker's. It must stop working on its next request, not at its next expiry.
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void confirmsAResetByMailSoATakeoverIsVisibleToTheAccountOwner() throws Exception {
        givenVerifiedAccount();
        forgotPassword(EMAIL).andExpect(status().isAccepted());
        String token = tokenFromMailContaining("Reset your Travel Planner password");
        mailer.clear();

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(mailer.lastMessage()).hasValueSatisfying(message -> {
            assertThat(message.subject()).isEqualTo("Your Travel Planner password was changed");
            assertThat(message.to()).isEqualTo(EMAIL);
        });
    }

    @Test
    void refusesToReuseAResetLink() throws Exception {
        givenVerifiedAccount();
        forgotPassword(EMAIL).andExpect(status().isAccepted());
        String token = tokenFromMailContaining("Reset your Travel Planner password");

        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
        resetPassword(token, "yet-another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_token"));
    }

    @Test
    void doesNotBurnTheResetLinkWhenTheNewPasswordFailsThePolicy() throws Exception {
        givenVerifiedAccount();
        forgotPassword(EMAIL).andExpect(status().isAccepted());
        String token = tokenFromMailContaining("Reset your Travel Planner password");

        resetPassword(token, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));

        // The recovery flow must not punish a typo with "ask for another link".
        resetPassword(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void answersForgotPasswordIdenticallyForAnUnknownAddressAndMailsNothing() throws Exception {
        givenVerifiedAccount();
        String known = bodyOf(forgotPassword(EMAIL).andExpect(status().isAccepted()));
        mailer.clear();

        String unknown = bodyOf(forgotPassword("nobody@example.com")
                .andExpect(status().isAccepted()));

        assertThat(unknown).isEqualTo(known);
        assertThat(mailer.outbox()).isEmpty();
    }

    @Test
    void refusesToMintALocalPasswordForAnAccountThatHasNone() throws Exception {
        // ADR 009 §4. Task 10 creates these through a provider; the row shape is what matters here.
        givenVerifiedAccount();
        jdbc.update("UPDATE \"user\" SET password_hash = NULL WHERE lower(email) = ?", EMAIL);
        mailer.clear();

        forgotPassword(EMAIL).andExpect(status().isAccepted());

        assertThat(mailer.outbox()).singleElement().satisfies(message ->
                assertThat(message.subject()).isEqualTo("About your Travel Planner account"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM account_token WHERE purpose = 'PASSWORD_RESET'", Integer.class))
                .describedAs("no reset token may be minted for a provider-only account")
                .isZero();
        assertThat(users.findByEmailIgnoreCase(EMAIL).orElseThrow().passwordHash()).isNull();
    }

    @Test
    void storesResetTokensHashedSoADumpYieldsNoWorkingLinks() throws Exception {
        givenVerifiedAccount();
        forgotPassword(EMAIL).andExpect(status().isAccepted());
        String rawToken = tokenFromMailContaining("Reset your Travel Planner password");

        List<String> hashes = jdbc.queryForList(
                "SELECT token_hash FROM account_token WHERE purpose = 'PASSWORD_RESET'", String.class);
        assertThat(hashes).singleElement().satisfies(hash ->
                assertThat(hash).hasSize(64).isNotEqualTo(rawToken));
    }

    // ---------------------------------------------------------------------------------------
    // UC-A12 — change password
    // ---------------------------------------------------------------------------------------

    @Test
    void changePasswordEndsEverySessionIncludingTheCallersOwn() throws Exception {
        givenVerifiedAccount();
        Cookie phone = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());
        Cookie laptop = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        MvcResult changed = mockMvc.perform(put("/api/v1/auth/password").with(csrf()).cookie(laptop)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current_password":"%s","new_password":"%s"}"""
                                .formatted(PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andReturn();

        // The point of the feature: a user changing their password because they believe they were
        // compromised is telling the system to evict everyone.
        mockMvc.perform(get("/api/v1/auth/me").cookie(phone)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").cookie(laptop)).andExpect(status().isUnauthorized());

        List<String> cleared = changed.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookieHeader(cleared, "tp_session")).contains("Max-Age=0");
        assertThat(cookieHeader(cleared, "tp_refresh")).contains("Max-Age=0");
        login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void refusesAChangeWithoutTheCorrectCurrentPassword() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(put("/api/v1/auth/password").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current_password":"not-the-password","new_password":"%s"}"""
                                .formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));

        // Unchanged, and the session survives: a failed attempt must not evict anybody.
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void requiresASessionToChangeAPassword() throws Exception {
        mockMvc.perform(put("/api/v1/auth/password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"current_password":"x","new_password":"%s"}""".formatted(NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // UC-A14 — delete account
    // ---------------------------------------------------------------------------------------

    @Test
    void deleteAnonymisesTheRowKeepsItAndTerminatesEverySession() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(delete("/api/v1/auth/me").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        // The row survives — PLAN §8 makes it the owner of every trip — and identifies nobody.
        assertThat(countUsers()).isOne();
        assertThat(jdbc.queryForList(
                "SELECT email, username, password_hash, enabled, deleted_at FROM \"user\""))
                .singleElement()
                .satisfies(row -> {
                    assertThat((String) row.get("email")).endsWith("@deleted.invalid");
                    assertThat(row.get("username")).isNull();
                    assertThat(row.get("password_hash")).isNull();
                    assertThat(row.get("enabled")).isEqualTo(false);
                    assertThat(row.get("deleted_at")).isNotNull();
                });

        // ADR 009's motivating example: without the token_version bump this still returns 200.
        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteClosesOutstandingMailedLinks() throws Exception {
        givenVerifiedAccount();
        forgotPassword(EMAIL).andExpect(status().isAccepted());
        String resetToken = tokenFromMailContaining("Reset your Travel Planner password");
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());

        mockMvc.perform(delete("/api/v1/auth/me").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        resetPassword(resetToken, NEW_PASSWORD).andExpect(status().isBadRequest());
    }

    @Test
    void deleteRequiresASession() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/me").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void releasesTheUsernameSoSomebodyElseMayTakeIt() throws Exception {
        givenVerifiedAccount();
        Cookie session = sessionCookieFrom(login(EMAIL, PASSWORD).andReturn());
        mockMvc.perform(delete("/api/v1/auth/me").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());
        mailer.clear();

        register("someone-else@example.com", USERNAME, PASSWORD).andExpect(status().isAccepted());

        assertThat(countUsers()).isEqualTo(2);
        assertThat(mailer.outbox()).extracting(MailMessage::subject)
                .contains("Confirm your email address");
    }

    // ---------------------------------------------------------------------------------------
    // ADR 006 — the new public surface is still CSRF-protected
    // ---------------------------------------------------------------------------------------

    @Test
    void rejectsALifecycleMutationThatDoesNotEchoTheCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}""".formatted(EMAIL)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    /** Registers, then follows the real verification link — no more back-door column updates. */
    private void givenVerifiedAccount() throws Exception {
        register(EMAIL, USERNAME, PASSWORD).andExpect(status().isAccepted());
        confirmVerification(tokenFromMailContaining("Confirm your email"))
                .andExpect(status().isNoContent());
        mailer.clear();
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

    private ResultActions confirmVerification(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email/confirm").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s"}""".formatted(token)));
    }

    private ResultActions resendVerification(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email/resend").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}""".formatted(email)));
    }

    private ResultActions forgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/forgot").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}""".formatted(email)));
    }

    private ResultActions resetPassword(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/reset").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","new_password":"%s"}""".formatted(token, newPassword)));
    }

    /**
     * Pulls the token out of the stub mailer's outbox — the same path a user takes, so what is
     * tested is the link that was actually mailed rather than a value read out of the database.
     */
    private String tokenFromMailContaining(String subjectFragment) {
        MailMessage message = mailer.outbox().stream()
                .filter(sent -> sent.subject().contains(subjectFragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no mail with subject containing '" + subjectFragment + "' in "
                                + mailer.outbox().stream().map(MailMessage::subject).toList()));

        String body = message.textBody();
        int start = body.indexOf("token=") + "token=".length();
        int end = start;
        while (end < body.length() && !Character.isWhitespace(body.charAt(end))) {
            end++;
        }
        return body.substring(start, end);
    }

    private int countUsers() {
        return jdbc.queryForObject("SELECT count(*) FROM \"user\"", Integer.class);
    }

    private static String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private static Cookie sessionCookieFrom(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("tp_session");
        assertThat(cookie).describedAs("cookie tp_session").isNotNull();
        return cookie;
    }

    private static String cookieHeader(List<String> setCookieHeaders, String name) {
        return setCookieHeaders.stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }
}
