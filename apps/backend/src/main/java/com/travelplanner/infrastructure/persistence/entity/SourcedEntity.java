package com.travelplanner.infrastructure.persistence.entity;

import java.time.Instant;

/**
 * Implemented by every catalogue row that cites a {@link KnowledgeSourceEntity} (V13-V17).
 *
 * <p>It exists so the provenance assembly can be written <em>once</em>. Nine tables carry the same
 * {@code (source_id, retrieved_at)} pair, and nine copies of "read the licence from the source but
 * the timestamp from the row" is nine chances to get the second half backwards. A single supertype
 * lets {@code KnowledgeProvenanceMapper} declare one method that MapStruct selects for all of them,
 * so the rule is stated in one place and compiled into every mapper.
 *
 * <p>Not a {@code @MappedSuperclass}: the tables share two columns, not an identity or a lifecycle,
 * and an inheritance hierarchy would put Hibernate's entity metadata in the middle of what is only
 * a read-side convenience.
 */
public interface SourcedEntity {

    /** The cited source. Lazy on every implementor — see {@link KnowledgeSourceEntity}. */
    KnowledgeSourceEntity getSource();

    /**
     * When <em>this row's</em> fact was taken from the source.
     *
     * <p>Deliberately not the source's own {@code retrieved_at}: the two describe different fetches,
     * and ADR 010 §6 measures its TTLs against this one. See {@code KnowledgeProvenanceMapper}.
     */
    Instant getRetrievedAt();
}
