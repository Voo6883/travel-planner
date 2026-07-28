package com.travelplanner.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.domain.enums.AdminAction;
import com.travelplanner.domain.model.AdminAuditEvent;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.AdminAuditPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import com.travelplanner.infrastructure.persistence.AbstractPostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Account administration end to end, over HTTP, against a real PostgreSQL (PLAN §4.0.6, UC-A15,
 * UC-A16).
 *
 * <p>This is the suite that proves task 12's Definition of Done. Four claims are asserted against
 * the database rather than against a mock, because each of them is exactly the kind that looks
 * right in a unit test and is wrong in production:
 *
 * <ol>
 *   <li><b>Authorisation.</b> Anonymous, {@code USER}, and {@code ADMIN} all reach the same URLs and
 *       get different answers, from the real filter chain with the real {@code ROLE_ADMIN} authority
 *       derived from the stored role value {@code ADMIN}.
 *   <li><b>Sessions really end</b> (ADR 009 §1). Asserted by reusing the target's own cookie after
 *       the action and expecting {@code 401} — not by verifying that a method was called.
 *   <li><b>Audit rows exist, and roll back with their transaction.</b> A trail that survives a
 *       rolled-back mutation asserts something that did not happen.
 *   <li><b>An administrator gains no cross-user trip access.</b>
 * </ol>
 *
 * <p>Testcontainers, so it lives in {@code src/test/integration} and never runs under
 * {@code ./gradlew test} — that suite must stay Docker-free (PLAN §4.0.2-K).
 */
@AutoConfigureMockMvc
class AdminApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@travelplanner.local";
    private static final String USER_EMAIL = "aisyah@example.com";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "a-brand-new-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepositoryPort users;

    @Autowired
    private AdminAuditPort auditTrail;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    private UUID adminId;
    private UUID targetId;

    @BeforeEach
    void resetAccountsAndTrail() {
        jdbc.execute("DELETE FROM audit_event");
        jdbc.execute("DELETE FROM login_attempt");
        jdbc.execute("DELETE FROM mail_rate_limit");
        jdbc.execute("DELETE FROM \"user\"");

        adminId = givenAccount(ADMIN_EMAIL, "rootadmin", "ADMIN");
        targetId = givenAccount(USER_EMAIL, "aisyah", "USER");
    }

    // ---------------------------------------------------------------------------------------
    // Authorisation boundaries — the same URLs, three callers, three answers
    // ---------------------------------------------------------------------------------------

    @Test
    void refusesEveryAdminEndpointToAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mockMvc.perform(get("/api/v1/admin/users/" + targetId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/admin/users/" + targetId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesEveryAdminEndpointToASignedInNonAdmin() throws Exception {
        Cookie session = sessionFor(USER_EMAIL);

        mockMvc.perform(get("/api/v1/admin/users").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        mockMvc.perform(put("/api/v1/admin/users/" + adminId).with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/users/" + adminId + "/reset-password")
                        .with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isForbidden());

        // A refused call is not a mutation, so it leaves no audit row — and, critically, no change.
        assertThat(auditRows()).isEmpty();
        assertThat(users.findById(adminId).orElseThrow().enabled()).isTrue();
    }

    @Test
    void admitsAnAccountWhoseStoredRoleValueIsAdmin() throws Exception {
        // The ADMIN / ROLE_ADMIN split (PLAN §4.0.6): the column holds `ADMIN`, the filter grants
        // `ROLE_ADMIN`, and `hasRole('ADMIN')` re-adds the prefix. Getting either half wrong denies
        // everybody, which is why this asserts against a row written as the plain value.
        assertThat(jdbc.queryForObject("SELECT role FROM \"user\" WHERE id = ?", String.class,
                adminId)).isEqualTo("ADMIN");

        mockMvc.perform(get("/api/v1/admin/users").cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    // ---------------------------------------------------------------------------------------
    // UC-A15 — the list and the detail
    // ---------------------------------------------------------------------------------------

    @Test
    void listsAccountsWithThePublishedPaginationEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users?page=0&page_size=1").cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.page_size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                // Account state only. A password hash reaching this array would hand every
                // administrator an offline cracking target for every account at once.
                .andExpect(jsonPath("$.items[0].password_hash").doesNotExist())
                .andExpect(jsonPath("$.items[0].user_id").exists());
    }

    @Test
    void rejectsASortFieldTheEndpointDoesNotPublish() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users?sort=email").cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void answersAnUnknownIdWithTheTypedUserNotFoundCode() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID())
                        .cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"));
    }

    // ---------------------------------------------------------------------------------------
    // ADR 009 §1 — the two mutations terminate sessions
    // ---------------------------------------------------------------------------------------

    @Test
    void disablingAnAccountTerminatesTheSessionItAlreadyHolds() throws Exception {
        Cookie victimSession = sessionFor(USER_EMAIL);
        mockMvc.perform(get("/api/v1/auth/me").cookie(victimSession)).andExpect(status().isOk());
        int versionBefore = users.findById(targetId).orElseThrow().tokenVersion();

        mockMvc.perform(put("/api/v1/admin/users/" + targetId).with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // ADR 009's motivating example. Without the token_version bump this still returns 200, and
        // a disabled account keeps working for up to thirty minutes.
        mockMvc.perform(get("/api/v1/auth/me").cookie(victimSession))
                .andExpect(status().isUnauthorized());
        User disabled = users.findById(targetId).orElseThrow();
        assertThat(disabled.tokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(disabled.sessionsValidAfter()).isNotNull();
        // …and the account cannot simply sign in again.
        login(USER_EMAIL, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_disabled"));
    }

    @Test
    void resettingAPasswordTerminatesEverySessionAndReplacesTheCredential() throws Exception {
        Cookie victimSession = sessionFor(USER_EMAIL);

        mockMvc.perform(put("/api/v1/admin/users/" + targetId + "/reset-password").with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        // The action exists for the "that account is compromised" case; leaving the intruder's
        // session live would achieve nothing.
        mockMvc.perform(get("/api/v1/auth/me").cookie(victimSession))
                .andExpect(status().isUnauthorized());
        login(USER_EMAIL, PASSWORD).andExpect(status().isUnauthorized());
        login(USER_EMAIL, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void reEnablingRestoresSignInWithoutResurrectingTheOldSession() throws Exception {
        Cookie victimSession = sessionFor(USER_EMAIL);
        setEnabled(targetId, false);

        setEnabled(targetId, true);

        login(USER_EMAIL, PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").cookie(victimSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesToLetAnAdministratorDisableTheirOwnAccount() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + adminId).with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        // ADMIN is the only role that can re-enable an account, so this would be unrecoverable.
        assertThat(users.findById(adminId).orElseThrow().enabled()).isTrue();
        assertThat(auditRows()).isEmpty();
    }

    @Test
    void refusesEveryMutationAgainstAnAccountItsOwnerClosed() throws Exception {
        Cookie victimSession = sessionFor(USER_EMAIL);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/auth/me").with(csrf()).cookie(victimSession))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/admin/users/" + targetId).with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("account_closed"));

        // V10's ck_user_deleted_has_no_password would reject the write anyway; a typed conflict is
        // a far better answer to an administrator than a constraint violation surfacing as a 500.
        mockMvc.perform(put("/api/v1/admin/users/" + targetId + "/reset-password").with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isConflict());
        assertThat(auditRows()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------
    // PLAN §4.0.6 — every mutation is audited, and only a mutation is
    // ---------------------------------------------------------------------------------------

    @Test
    void writesOneAuditRowPerMutationCarryingActorTargetActionTimeAndResult() throws Exception {
        setEnabled(targetId, false);
        setEnabled(targetId, true);
        mockMvc.perform(put("/api/v1/admin/users/" + targetId + "/reset-password").with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = auditRows();
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.get("action"))
                .containsExactly("DISABLE_USER", "ENABLE_USER", "RESET_PASSWORD");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("actor_user_id")).isEqualTo(adminId);
            assertThat(row.get("target_user_id")).isEqualTo(targetId);
            assertThat(row.get("result")).isEqualTo("SUCCESS");
            assertThat(row.get("created_at")).isNotNull();
            // The correlation id that joins this row to the log lines the same request produced.
            assertThat(row.get("request_id")).isNotNull();
        });
    }

    @Test
    void storesNoCredentialInTheAuditTrail() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + targetId + "/reset-password").with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"new_password\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT * FROM audit_event").toString())
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(USER_EMAIL);
    }

    @Test
    void doesNotAuditAReadOnlyLookup() throws Exception {
        // Auditing page views would bury the handful of rows that matter under thousands that do
        // not, which is how an audit trail stops being read.
        mockMvc.perform(get("/api/v1/admin/users").cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users/" + targetId).cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isOk());

        assertThat(auditRows()).isEmpty();
    }

    @Test
    void anAuditRowRollsBackWithTheTransactionThatWroteIt() {
        // The property that makes the trail trustworthy: it records what happened to the database,
        // never what was attempted. AiCallLogPort deliberately does the opposite (REQUIRES_NEW), so
        // this is a decision worth pinning rather than an accident of configuration.
        TransactionTemplate template = new TransactionTemplate(transactions);

        template.execute(status -> {
            auditTrail.record(AdminAuditEvent.of(adminId, targetId, AdminAction.DISABLE_USER));
            status.setRollbackOnly();
            return null;
        });

        assertThat(auditRows()).isEmpty();

        // …and the same write commits when its transaction does, so the emptiness above is the
        // rollback rather than a port that silently drops everything.
        template.executeWithoutResult(status ->
                auditTrail.record(AdminAuditEvent.of(adminId, targetId, AdminAction.DISABLE_USER)));
        assertThat(auditRows()).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------
    // "Do not let an admin read or mutate another user's trips"
    // ---------------------------------------------------------------------------------------

    @Test
    void givesAnAdministratorNoRouteToAnotherUsersTrips() throws Exception {
        // There is no /admin/trips path, and none of the four admin operations returns trip data.
        mockMvc.perform(get("/api/v1/admin/users/" + targetId + "/trips")
                        .cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/users/" + targetId).cookie(sessionFor(ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips").doesNotExist());
    }

    @Test
    void leavesTripRepositoryPortWithoutAnUnscopedLookup() {
        // The structural half of the same rule. Every read on this port takes the owner's id, and
        // an unscoped findById would make cross-user leakage a one-line mistake in a service
        // somebody writes six tasks from now.
        List<String> methods = List.of(TripRepositoryPort.class.getMethods()).stream()
                .map(Method::getName)
                .toList();

        assertThat(methods).doesNotContain("findById", "findAll", "findAllTrips");
        assertThat(methods)
                .filteredOn(name -> name.startsWith("find") || name.startsWith("delete"))
                .isNotEmpty()
                .allSatisfy(name -> assertThat(name)
                        .describedAs("every trip lookup must be owner-scoped (PLAN §4.0.2-L)")
                        .containsIgnoringCase("UserId"));
    }

    // ---------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------

    /**
     * Registers through the real endpoint, then promotes and verifies by SQL.
     *
     * <p>Registration is the only way to obtain a correctly hashed password, and there is no API
     * that grants {@code ADMIN} — deliberately, since an endpoint able to do so would be a
     * privilege-escalation surface. So the role is set the way the seeder would set it.
     */
    private UUID givenAccount(String email, String username, String role) {
        try {
            mockMvc.perform(post("/api/v1/auth/register").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","username":"%s","password":"%s"}"""
                                    .formatted(email, username, PASSWORD)))
                    .andExpect(status().isAccepted());
        } catch (Exception unexpected) {
            throw new IllegalStateException("could not register " + email, unexpected);
        }
        jdbc.update("UPDATE \"user\" SET role = ?, email_verified = true WHERE lower(email) = ?",
                role, email.toLowerCase(java.util.Locale.ROOT));
        return users.findByEmailIgnoreCase(email).orElseThrow().id();
    }

    private Cookie sessionFor(String email) throws Exception {
        MvcResult result = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("tp_session");
        assertThat(cookie).describedAs("cookie tp_session for %s", email).isNotNull();
        return cookie;
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"login":"%s","password":"%s"}""".formatted(email, password)));
    }

    private void setEnabled(UUID userId, boolean enabled) throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/" + userId).with(csrf())
                        .cookie(sessionFor(ADMIN_EMAIL)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":" + enabled + "}"))
                .andExpect(status().isOk());
    }

    private List<Map<String, Object>> auditRows() {
        return jdbc.queryForList("SELECT * FROM audit_event ORDER BY created_at, action");
    }
}
