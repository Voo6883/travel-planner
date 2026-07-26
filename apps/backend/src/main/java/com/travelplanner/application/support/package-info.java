/**
 * Cross-cutting building blocks every application service reuses.
 *
 * <p>Currently one thing: {@link com.travelplanner.application.support.TransactionalWrite}, the
 * transaction and deadlock-retry template from PLAN §4.0.2-E2.
 *
 * <h2>Write-use-case checklist (PLAN §4.0.2-H2)</h2>
 *
 * <ul>
 *   <li>The transactional boundary is the <em>service</em> method — never a controller, never an
 *       adapter.</li>
 *   <li>Every table a use-case touches is written inside that one boundary, so a failure leaves no
 *       partial state.</li>
 *   <li>Multi-table writes follow the lock order documented in
 *       {@code com.travelplanner.infrastructure.persistence}: {@code user → trip → trip_brief}.</li>
 *   <li>Aggregates the agent can also write carry a version and are guarded by
 *       {@link com.travelplanner.domain.model.Versioned#requireVersion} before mutation.</li>
 *   <li>LLM and supplier IO happens <em>before</em> the annotated method is entered, never inside
 *       it.</li>
 * </ul>
 */
package com.travelplanner.application.support;
