package com.travelplanner.infrastructure.persistence.repository;

import com.travelplanner.infrastructure.persistence.entity.AuditEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to {@code audit_event}. Reached only through
 * {@code AuditEventRepositoryAdapter} — PLAN §4.0.2-H forbids repositories in {@code application/}.
 *
 * <p>Insert-only in v1. There is no admin-facing audit query endpoint in task 12's scope, and the
 * two indexes V12 creates exist for the operator running SQL rather than for a screen. A read API
 * over this table is a separate work order.
 */
public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {
}
