/**
 * JPA entities, Spring Data repositories, and the adapters that implement
 * {@code com.travelplanner.domain.port}.
 *
 * <h2>Entities never leave this package</h2>
 *
 * {@code *Entity} types are mutable, lazily-initialised, and tied to a persistence context. Handing
 * one to a service or a controller leaks all three properties: a caller can mutate persistent state
 * without a transaction, trigger a lazy load after the session closed, or serialise an internal
 * column straight onto the wire. Every port method therefore returns a domain type, produced by an
 * explicit MapStruct mapper (PLAN §4.0.2-H, §4.0.2-I). ArchUnit enforces this from task 15.
 *
 * <h2>Lock order (PLAN §4.0.2-E2)</h2>
 *
 * A multi-table write acquires row locks in this order, always:
 *
 * <pre>
 *   user  →  trip  →  trip_brief  →  (itinerary_day → itinerary_item → booking, later tasks)
 * </pre>
 *
 * The order follows foreign-key direction, parent before child. Two transactions that both touch
 * {@code trip} and {@code trip_brief} in this order can queue behind one another but cannot form a
 * cycle; one transaction taking {@code trip_brief} first would deadlock against the other under
 * ordinary concurrent editing. Later tasks extend the chain to the right — they must not insert a
 * table in the middle without updating this note.
 *
 * <h2>Timestamps</h2>
 *
 * Every {@code timestamptz} column maps to {@link java.time.Instant}. There is no
 * {@code LocalDateTime} anywhere in persistence: it has no offset, so what it means depends on the
 * JVM's default timezone, and the value read back on a machine in another zone is a different
 * moment. Calendar dates the traveller chose ({@code start_date}, {@code end_date}) are the
 * opposite case and stay {@link java.time.LocalDate} — "arrive on 3 April" is not an instant.
 * {@code spring.jpa.properties.hibernate.jdbc.time_zone=UTC} pins the JDBC side.
 *
 * <h2>Identifiers</h2>
 *
 * UUID v4, assigned by the domain factory before the aggregate is ever persisted (PLAN §4.0.2-H).
 * Application-assigned rather than database-generated, because an aggregate must be a complete,
 * referenceable object the moment it is constructed — an id that only exists after a flush cannot
 * be used to build the rest of a write in the same transaction, and cannot be logged if that write
 * fails.
 *
 * <h2>Why the adapters are {@code @ConditionalOnProperty("spring.datasource.url")}</h2>
 *
 * Same condition, and same reason, as {@code DatabaseReadinessContributor} from task 04. The unit
 * suite excludes {@code DataSourceAutoConfiguration} so that {@code ./gradlew test} needs neither
 * Docker nor Postgres; with no datasource, Spring Data contributes no repository beans, and an
 * unconditional adapter would fail context startup on a missing dependency. The condition is on the
 * property rather than on the bean because {@code @ConditionalOnBean} against a component scan is
 * evaluated before auto-configured beans exist and would be order-dependent.
 *
 * <h2>Transactions</h2>
 *
 * Adapters participate in the caller's transaction and never declare their own propagation
 * (PLAN §4.0.2-H). The transactional boundary is the service method, annotated
 * {@code @TransactionalWrite} — see {@code com.travelplanner.application.support}.
 */
package com.travelplanner.infrastructure.persistence;
