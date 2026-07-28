package com.travelplanner.domain.port;

import com.travelplanner.domain.model.AdminAuditEvent;

/**
 * Persists the administrative audit trail (PLAN §4.0.6). Implemented in
 * {@code infrastructure/persistence/}.
 *
 * <h2>How this differs from {@link AiCallLogPort}, and why</h2>
 *
 * <p>{@code AiCallLogPort} runs {@code REQUIRES_NEW} and swallows its own failures: losing a
 * metrics row is cheaper than failing a user's request. Both properties are wrong here, and
 * inverting them is the whole point of this port.
 *
 * <ul>
 *   <li><b>It joins the caller's transaction.</b> An audit row must roll back with the change it
 *       describes. A row that survived a rolled-back mutation would assert that an administrator
 *       reset someone's password when they did not — an audit trail that lies is worse than none.
 *   <li><b>It must not swallow failures.</b> If the row cannot be written, the mutation must not be
 *       reported as done. PLAN §4.0.6 requires <em>every</em> admin mutation to be audited, and
 *       "audited unless the insert failed" is not that requirement.
 * </ul>
 */
public interface AdminAuditPort {

    /**
     * @param event carries no password, no password hash, and no email address — see
     *     {@link AdminAuditEvent}, where that is a property of the type rather than of this
     *     method's discipline
     */
    void record(AdminAuditEvent event);
}
