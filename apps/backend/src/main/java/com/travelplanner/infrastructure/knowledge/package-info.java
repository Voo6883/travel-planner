/**
 * The sample Travel Knowledge Base seeder (task 17, ADR 010 §3).
 *
 * <h2>What lives here</h2>
 *
 * Reading {@code classpath:knowledge/sample/*.json} and writing it into the V13–V18 catalogue and
 * embedding tables. The format those files must follow is documented in
 * {@code src/main/resources/knowledge/sample/README.md}, which is the contract tasks 40 and 41 are
 * written against.
 *
 * <h2>Why this is not in {@code infrastructure.persistence}</h2>
 *
 * Everything in that package is a <em>read</em> adapter behind {@code KnowledgePort}, and its
 * {@code package-info} states the rule that entities never leave it. Seeding is a different job: a
 * one-shot, profile-gated, development-only write that has no port and no domain caller. Putting it
 * beside the read adapters would mix a startup side effect into the package that answers planning
 * requests.
 *
 * <p>The cost of the separation is that this package constructs {@code *Entity} objects, which is
 * the one place outside {@code infrastructure.persistence} that does. It is a deliberate, narrow
 * exception and it does not weaken the ArchUnit boundary
 * ({@code LayerRulesTest.jpaConfinedToPersistenceEntities}): no class here imports
 * {@code jakarta.persistence} or {@code org.hibernate}, so JPA itself stays confined. Entities are
 * constructed and handed straight to a Spring Data repository — none is returned from a public
 * method, so none can become a transport type. The alternative, a second set of write-side domain
 * records used by nothing else, would be more code and no more safety.
 *
 * <h2>Why the embedding writes are not JPA</h2>
 *
 * Hibernate cannot map {@code vector(1536)}, so V18's two embedding tables have no entity at all —
 * the same reason {@code KnowledgeVectorSearch} reads them with native SQL. This package writes them
 * with {@code JdbcTemplate} rather than an {@code EntityManager}, because an {@code EntityManager}
 * would mean importing {@code jakarta.persistence} outside the persistence package and breaking the
 * ArchUnit rule for a query that gains nothing from JPA.
 *
 * <h2>The gates</h2>
 *
 * Every bean here carries three conditions, all of which must hold:
 *
 * <ul>
 *   <li>{@code @Profile({"dev", "docker", "local"})} — an allow-list, following
 *       {@code DevAdminSeeder}. A deny-list on {@code prod} would silently admit every future
 *       profile somebody invents ({@code staging}, {@code preview}, {@code demo}); an allow-list
 *       admits nothing until a person adds it.</li>
 *   <li>{@code travelplanner.knowledge.sample-seed.enabled=true} — absent by default, so a
 *       developer opts in rather than discovering sample rows in their database.</li>
 *   <li>{@code @RequiresDatabase} — there is nothing to seed without a datasource, and
 *       {@code ./gradlew test} runs with none.</li>
 * </ul>
 */
package com.travelplanner.infrastructure.knowledge;
