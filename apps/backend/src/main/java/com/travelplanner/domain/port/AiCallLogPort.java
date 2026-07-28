package com.travelplanner.domain.port;

import com.travelplanner.domain.ai.AiCallRecord;

/**
 * Persists AI observability rows (PLAN §5.3, §8; backlog S2-5 "token count persisted").
 *
 * <p>A port rather than a direct repository call so the AI layer never imports JPA, and so a test
 * can assert on what would have been logged without a database.
 *
 * <p>Implementations must not throw into the caller. Observability failing is not a reason for a
 * user's trip planning to fail — a dropped metrics row is cheaper than a 500, and a logging write
 * that can abort a request is a self-inflicted outage.
 */
public interface AiCallLogPort {

    /**
     * @param record contains no prompt text, no completion text, and no PII — see
     *     {@link AiCallRecord}, where that is a property of the type rather than of this method's
     *     discipline
     */
    void record(AiCallRecord record);
}
