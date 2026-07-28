package com.travelplanner.application.admin;

import static com.travelplanner.application.admin.AdminTestFakes.account;
import static com.travelplanner.application.admin.AdminTestFakes.actorFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.api.dto.page.PageQuery;
import com.travelplanner.application.account.AccountStore;
import com.travelplanner.application.account.AccountTestFakes;
import com.travelplanner.application.admin.AdminTestFakes.FakeAdminAudit;
import com.travelplanner.application.auth.AuthTestFakes.FakeIdentities;
import com.travelplanner.application.auth.AuthTestFakes.FakeUsers;
import com.travelplanner.application.auth.SessionRevocationReason;
import com.travelplanner.domain.enums.AdminAction;
import com.travelplanner.domain.enums.AdminActionResult;
import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.exception.AccountClosedException;
import com.travelplanner.domain.exception.ForbiddenException;
import com.travelplanner.domain.exception.UserNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The admin rules, without a database (PLAN §4.0.2-K keeps {@code ./gradlew test} Docker-free).
 *
 * <p>Three properties are load-bearing and each has its own tests:
 *
 * <ul>
 *   <li><b>Both mutations terminate sessions</b> (ADR 009 §1). Asserted through
 *       {@code tokenVersion} and {@code sessionsValidAfter}, which is what the authentication filter
 *       actually reads — not by verifying that a method was called.
 *   <li><b>Every mutation is audited, and nothing else is</b> (PLAN §4.0.6). A refused action must
 *       leave no row: an audit trail that records attempts as though they were changes is worse
 *       than one that records nothing.
 *   <li><b>The refusals are typed.</b> {@code user_not_found}, {@code account_closed},
 *       {@code forbidden}, {@code validation_failed} — each reachable, each distinguishable.
 * </ul>
 */
class AdminUserServiceTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final String NEW_PASSWORD = "a-brand-new-password";

    private final FakeUsers users = new FakeUsers();
    private final FakeIdentities identities = new FakeIdentities();
    private final FakeAdminAudit audit = new FakeAdminAudit();

    private AdminUserService service;
    private User admin;
    private UserContext actor;

    @BeforeEach
    void wireTheAdminSlice() {
        AdminUserDirectory directory = new AdminUserDirectory(users, identities);
        AdminUserStore store =
                new AdminUserStore(users, audit, AccountTestFakes.revocation(users));
        AccountStore accounts = AccountTestFakes.accountStore(users);
        service = new AdminUserService(directory, store, accounts);

        admin = users.save(account("admin@travelplanner.local", Role.ADMIN, EPOCH));
        actor = actorFor(admin);
    }

    // ---------------------------------------------------------------------------------------
    // PLAN §4.0.6 + ADR 009 §1 — disable terminates sessions
    // ---------------------------------------------------------------------------------------

    @Test
    void disablingAnAccountTerminatesEverySessionItHolds() {
        User target = givenAccount();

        AdminUserView updated = service.setEnabled(new SetUserEnabledCommand(target.id(), false),
                actor);

        assertThat(updated.account().enabled()).isFalse();
        // ADR 009 §1's two independent checks, both of which JwtAuthenticationFilter reads on every
        // request. Without them "disabled" means disabled in up to thirty minutes.
        User stored = users.findById(target.id()).orElseThrow();
        assertThat(stored.tokenVersion()).isEqualTo(target.tokenVersion() + 1);
        assertThat(stored.sessionsValidAfter()).isNotNull();
    }

    @Test
    void auditsADisableWithActorTargetActionAndResult() {
        User target = givenAccount();

        service.setEnabled(new SetUserEnabledCommand(target.id(), false), actor);

        assertThat(audit.only()).satisfies(event -> {
            assertThat(event.actorUserId()).isEqualTo(admin.id());
            assertThat(event.targetUserId()).isEqualTo(target.id());
            assertThat(event.action()).isEqualTo(AdminAction.DISABLE_USER);
            assertThat(event.result()).isEqualTo(AdminActionResult.SUCCESS);
            assertThat(event.occurredAt()).isNotNull();
        });
    }

    @Test
    void enablingRestoresAccessWithoutTerminatingAnything() {
        User target = users.save(givenAccount().withEnabled(false, EPOCH));
        int versionBefore = target.tokenVersion();

        service.setEnabled(new SetUserEnabledCommand(target.id(), true), actor);

        assertThat(users.findById(target.id()).orElseThrow().enabled()).isTrue();
        // Enabling grants access; it does not resurrect the sessions the disable ended, and a bump
        // here would sign out an administrator who fixed their own mistake.
        assertThat(users.findById(target.id()).orElseThrow().tokenVersion()).isEqualTo(versionBefore);
        assertThat(audit.only().action()).isEqualTo(AdminAction.ENABLE_USER);
    }

    // ---------------------------------------------------------------------------------------
    // UC-A16 + ADR 009 §1 — reset terminates sessions
    // ---------------------------------------------------------------------------------------

    @Test
    void resettingAPasswordReplacesItAndTerminatesEverySession() {
        User target = givenAccount();

        service.resetPassword(new AdminResetPasswordCommand(target.id(), NEW_PASSWORD), actor);

        User stored = users.findById(target.id()).orElseThrow();
        assertThat(stored.passwordHash()).isEqualTo("hash:" + NEW_PASSWORD);
        // The action exists for the "that account is compromised" case, so an intruder's session
        // has to stop working now rather than at its next expiry.
        assertThat(stored.tokenVersion()).isEqualTo(target.tokenVersion() + 1);
        assertThat(stored.sessionsValidAfter()).isNotNull();
        assertThat(audit.only().action()).isEqualTo(AdminAction.RESET_PASSWORD);
    }

    @Test
    void revokesThroughTheSharedServiceRatherThanTouchingTokenVersion() {
        // AGENTS.md forbids writing token_version directly. SessionRevocationService does three
        // things together, and an endpoint that reimplements it will eventually do two — so the
        // assertion is that the shared path ran, not merely that the counter moved.
        User target = givenAccount();

        service.setEnabled(new SetUserEnabledCommand(target.id(), false), actor);
        service.resetPassword(new AdminResetPasswordCommand(target.id(), NEW_PASSWORD), actor);

        assertThat(users.revocations).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------
    // Typed refusals — and none of them writes an audit row
    // ---------------------------------------------------------------------------------------

    @Test
    void refusesToDisableTheAdministratorsOwnAccount() {
        // ADMIN is the only role that can re-enable an account, so this would be a lockout with no
        // recovery path.
        assertThatThrownBy(() -> service.setEnabled(new SetUserEnabledCommand(admin.id(), false),
                actor))
                .isInstanceOf(ForbiddenException.class);

        assertThat(users.findById(admin.id()).orElseThrow().enabled()).isTrue();
        assertThat(users.revocations).isZero();
        assertThat(audit.recorded).isEmpty();
    }

    @Test
    void allowsAnAdministratorToDisableAnotherAdministrator() {
        // The guard is about self-lockout, not about rank. A second admin account must remain
        // administrable or a compromised one could never be switched off.
        User other = users.save(account("second@travelplanner.local", Role.ADMIN, EPOCH));

        service.setEnabled(new SetUserEnabledCommand(other.id(), false), actor);

        assertThat(users.findById(other.id()).orElseThrow().enabled()).isFalse();
    }

    @Test
    void answersAnUnknownIdWithATypedUserNotFound() {
        assertThatThrownBy(() -> service.setEnabled(
                new SetUserEnabledCommand(UUID.randomUUID(), false), actor))
                .isInstanceOfSatisfying(UserNotFoundException.class, failure ->
                        assertThat(failure.code()).isEqualTo("user_not_found"));

        assertThat(audit.recorded).isEmpty();
    }

    @Test
    void refusesEveryMutationAgainstAClosedAccount() {
        User closed = users.save(User.anonymised(givenAccount(), EPOCH));

        assertThatThrownBy(() -> service.setEnabled(new SetUserEnabledCommand(closed.id(), true),
                actor)).isInstanceOf(AccountClosedException.class);
        assertThatThrownBy(() -> service.resetPassword(
                new AdminResetPasswordCommand(closed.id(), NEW_PASSWORD), actor))
                .isInstanceOf(AccountClosedException.class);

        assertThat(audit.recorded).isEmpty();
    }

    @Test
    void refusesToMintALocalPasswordForAProviderOnlyAccount() {
        // ADR 009 §4. Doing it on an administrator's say-so rather than the owner's would add a
        // second way into an account whose owner deliberately has only one.
        User providerOnly = users.save(givenAccount().withPasswordHash(null, EPOCH));

        assertThatThrownBy(() -> service.resetPassword(
                new AdminResetPasswordCommand(providerOnly.id(), NEW_PASSWORD), actor))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(users.findById(providerOnly.id()).orElseThrow().passwordHash()).isNull();
        assertThat(users.revocations).isZero();
        assertThat(audit.recorded).isEmpty();
    }

    @Test
    void rejectsAWeakReplacementPasswordWithoutEvictingAnybody() {
        User target = givenAccount();

        assertThatThrownBy(() -> service.resetPassword(
                new AdminResetPasswordCommand(target.id(), "short"), actor))
                .isInstanceOf(ValidationFailedException.class);

        assertThat(users.findById(target.id()).orElseThrow().passwordHash())
                .isEqualTo(target.passwordHash());
        assertThat(users.revocations).isZero();
        assertThat(audit.recorded).isEmpty();
    }

    @Test
    void keepsTheReplacementPasswordOutOfTheCommandsOwnStringRepresentation() {
        // A command object is exactly the kind of thing that lands in a debug log or an exception
        // message by accident, and a record prints every component by default.
        AdminResetPasswordCommand command =
                new AdminResetPasswordCommand(UUID.randomUUID(), NEW_PASSWORD);

        assertThat(command.toString()).doesNotContain(NEW_PASSWORD).contains("***");
    }

    // ---------------------------------------------------------------------------------------
    // UC-A15 — the list
    // ---------------------------------------------------------------------------------------

    @Test
    void listsEveryAccountNewestFirstIncludingDisabledAndClosedOnes() {
        User older = users.save(account("older@example.com", Role.USER, EPOCH.plusSeconds(10)));
        User newer = users.save(account("newer@example.com", Role.USER, EPOCH.plusSeconds(20)));
        users.save(newer.withEnabled(false, EPOCH));

        AdminUserPage page = directory().list(PageQuery.of(null, null, null));

        // An administrator who cannot see a disabled account cannot re-enable it.
        assertThat(page.items()).extracting(User::id)
                .containsExactly(newer.id(), older.id(), admin.id());
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void pagesWithoutRepeatingOrLosingAnAccountCreatedInTheSameInstant() {
        // Two rows with identical created_at: without the id tiebreaker the two pages are free to
        // disagree, so one account appears twice and another never appears at all.
        Instant sameMoment = EPOCH.plus(1, ChronoUnit.HOURS);
        users.save(account("a@example.com", Role.USER, sameMoment));
        users.save(account("b@example.com", Role.USER, sameMoment));

        AdminUserPage first = directory().list(PageQuery.of(0, 2, null));
        AdminUserPage second = directory().list(PageQuery.of(1, 2, null));

        assertThat(first.items()).hasSize(2);
        assertThat(second.items()).hasSize(1);
        assertThat(first.items()).extracting(User::id)
                .doesNotContainAnyElementsOf(second.items().stream().map(User::id).toList());
    }

    @Test
    void rejectsASortFieldTheEndpointDoesNotPublish() {
        assertThatThrownBy(() -> directory().list(PageQuery.of(null, null, "email")))
                .isInstanceOf(ValidationFailedException.class);
        assertThat(directory().list(PageQuery.of(null, null, "created_at")).items()).isNotEmpty();
    }

    @Test
    void detailReducesThePasswordToTheSingleBitAnAdministratorNeeds() {
        // ADR 009 §4's marker, and the only thing about a credential this surface publishes: the
        // response DTO has no field able to carry the hash, which is why it cannot leak one.
        User target = givenAccount();

        AdminUserView view = directory().detail(target.id());

        assertThat(view.hasLocalPassword()).isTrue();
        assertThat(directory().detail(
                users.save(target.withPasswordHash(null, EPOCH)).id()).hasLocalPassword()).isFalse();
        assertThat(view.linkedProviders()).isEmpty();
    }

    @Test
    void detailAnswersAnUnknownIdWithATypedUserNotFound() {
        assertThatThrownBy(() -> directory().detail(UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }

    // ---------------------------------------------------------------------------------------
    // The degraded outcome the audit table has to be able to express
    // ---------------------------------------------------------------------------------------

    @Test
    void reportsPartialWhenRevocationFindsNoAccountToRevoke() {
        // A disable that commits while its revocation reported nothing means the account may still
        // hold a live token. That is exactly the condition an operator has to be able to find, so
        // it must not be recorded as an unqualified success.
        FakeUsers empty = new FakeUsers();
        AdminUserStore store =
                new AdminUserStore(empty, audit, AccountTestFakes.revocation(empty));

        assertThat(store.terminateSessions(UUID.randomUUID(),
                SessionRevocationReason.ADMIN_DISABLED)).isEqualTo(AdminActionResult.PARTIAL);
    }

    private AdminUserDirectory directory() {
        return new AdminUserDirectory(users, identities);
    }

    private User givenAccount() {
        return users.save(account("aisyah@example.com", Role.USER, EPOCH.plusSeconds(60)));
    }
}
