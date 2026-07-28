package com.travelplanner.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class UserContextTest {

    @Test
    void requiresAUserIdBecauseEveryQueryIsScopedByIt() {
        assertThatThrownBy(() -> new UserContext(null, "a@b.com", List.of(), true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void treatsAbsentRolesAsNoRolesRatherThanNull() {
        UserContext context = new UserContext(UUID.randomUUID(), "a@b.com", null, true);

        assertThat(context.roles()).isEmpty();
        assertThat(context.isAdmin()).isFalse();
    }

    @Test
    void carriesEmailVerifiedSoTheUcA08GateHasSomethingToRead() {
        // Follow-up F-14, decided in task 08: sourced from a per-request user lookup rather than
        // from a token claim, so it cannot go stale inside a live session (ADR 009 §2).
        UUID userId = UUID.randomUUID();

        assertThat(new UserContext(userId, "a@b.com", List.of(), true).emailVerified()).isTrue();
        assertThat(new UserContext(userId, "a@b.com", List.of(), false).emailVerified()).isFalse();
    }

    @Test
    void theSingleRoleShortcutProducesAnUnverifiedContext() {
        // The safe default is the one that keeps a verification gate closed.
        assertThat(UserContext.of(UUID.randomUUID(), "a@b.com", Role.USER).emailVerified()).isFalse();
    }

    @Test
    void copiesTheRoleListSoACallerCannotEscalateAfterConstruction() {
        List<String> mutable = new ArrayList<>(List.of(Role.USER.name()));
        UserContext context = new UserContext(UUID.randomUUID(), "a@b.com", mutable, true);

        mutable.add(Role.ADMIN.name());

        assertThat(context.isAdmin()).isFalse();
        assertThat(context.roles()).containsExactly(Role.USER.name());
    }

    @Test
    void hasRoleComparesAgainstTheEnumRatherThanAStringLiteral() {
        UserContext admin = UserContext.of(UUID.randomUUID(), "admin@b.com", Role.ADMIN);

        assertThat(admin.hasRole(Role.ADMIN)).isTrue();
        assertThat(admin.hasRole(Role.USER)).isFalse();
        assertThat(admin.hasRole(null)).isFalse();
        assertThat(admin.isAdmin()).isTrue();
    }
}
