package com.travelplanner.application.admin;

import com.travelplanner.api.dto.page.PageQuery;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.exception.UserNotFoundException;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.model.User;
import com.travelplanner.domain.port.UserIdentityRepositoryPort;
import com.travelplanner.domain.port.UserRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read half of account administration (UC-A15): list accounts, and load one.
 *
 * <p>Split from {@link AdminUserStore} so neither class needs four collaborators — the read side
 * needs the identity repository for {@code linked_providers} and the write side needs the audit
 * port and the revocation service, and one class holding all four would exceed the three-parameter
 * ceiling {@code AGENTS.md} sets. It is also the honest split: nothing here mutates anything, so
 * nothing here is audited.
 *
 * <p><b>Reads are not audited, deliberately.</b> PLAN §4.0.6 requires every admin <em>mutation</em>
 * to be recorded. Auditing page views as well would bury the handful of rows that matter under
 * thousands that do not, which is how an audit trail stops being read.
 */
@Service
@RequiresDatabase
public class AdminUserDirectory {

    /**
     * The only sortable column this endpoint publishes. Every additional one is an index the table
     * has to carry and a query plan somebody has to justify; none of the others has a use case, and
     * the contract says so, so an unknown field is a typed {@code validation_failed} rather than a
     * silently ignored parameter.
     */
    static final String SORT_FIELD = "created_at";

    private final UserRepositoryPort users;
    private final UserIdentityRepositoryPort identities;

    public AdminUserDirectory(UserRepositoryPort users, UserIdentityRepositoryPort identities) {
        this.users = users;
        this.identities = identities;
    }

    /**
     * UC-A15. Includes disabled and closed accounts — an administrator who cannot see a disabled
     * account cannot re-enable it.
     *
     * @throws ValidationFailedException when {@code sort} names a field this endpoint does not
     *         publish
     */
    @Transactional(readOnly = true)
    public AdminUserPage list(PageQuery query) {
        requireSupportedSort(query);
        return new AdminUserPage(
                users.findPage(query.page(), query.pageSize(), query.descending()),
                users.countAll());
    }

    /** UC-A15. @throws UserNotFoundException when no account has this id */
    @Transactional(readOnly = true)
    public AdminUserView detail(UUID userId) {
        User account = require(userId);
        return AdminUserView.of(account, identities.findAllByUserId(userId));
    }

    /**
     * The account, or a typed 404. Used by {@link AdminUserService} before every mutation, so a
     * mutation can never be attempted against an id that does not resolve.
     *
     * @throws UserNotFoundException when no account has this id
     */
    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private static void requireSupportedSort(PageQuery query) {
        if (!SORT_FIELD.equals(query.sortField())) {
            throw ValidationFailedException.field("sort",
                    "must be '" + SORT_FIELD + "' or '-" + SORT_FIELD + "'");
        }
    }
}
