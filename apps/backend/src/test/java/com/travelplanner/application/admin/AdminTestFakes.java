package com.travelplanner.application.admin;

import com.travelplanner.domain.enums.Role;
import com.travelplanner.domain.model.AdminAuditEvent;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.AdminAuditPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** In-memory audit trail and account fixtures for the admin unit tests. */
public final class AdminTestFakes {

    private AdminTestFakes() {
    }

    /**
     * An account with an explicit creation instant, so paging and ordering can be asserted without
     * sleeping. {@code AuthTestFakes.user} stamps {@code Instant.now()} for every row, which makes
     * "newest first" untestable.
     */
    public static User account(String email, Role role, Instant createdAt) {
        return new User(UUID.randomUUID(), email.split("@")[0], email, "hash:secret", true, role,
                true, 0, null, null, createdAt, createdAt);
    }

    /** The signed-in administrator, as the security filter would have assembled it. */
    public static UserContext actorFor(User admin) {
        return new UserContext(admin.id(), admin.email(), List.of(admin.role().name()), true);
    }

    /**
     * Records what was written and nothing more.
     *
     * <p>Transaction behaviour — that a row rolls back with the mutation it describes — is not
     * expressible against a fake and is asserted by {@code AdminApiIntegrationTest} against a real
     * PostgreSQL. What this fake proves is the half that is a decision rather than a configuration:
     * <em>which</em> actions produce a row, and which deliberately produce none.
     */
    public static final class FakeAdminAudit implements AdminAuditPort {

        public final List<AdminAuditEvent> recorded = new ArrayList<>();
        /** When set, every write throws — the "the audit row could not be stored" case. */
        public RuntimeException failure;

        @Override
        public void record(AdminAuditEvent event) {
            if (failure != null) {
                throw failure;
            }
            recorded.add(event);
        }

        public AdminAuditEvent only() {
            if (recorded.size() != 1) {
                throw new AssertionError("expected exactly one audit event, got " + recorded.size());
            }
            return recorded.get(0);
        }
    }
}
