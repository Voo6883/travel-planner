package com.travelplanner.application.knowledge;

import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.port.KnowledgePort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The coverage question, answered in one place (ADR 010 §4).
 *
 * <p>Two callers need the same answer and must not disagree about it: the destination picker, which
 * lists what a user may plan, and the refusal path, where {@code destination_not_covered} has to
 * name the alternatives it is offering instead. A controller that queried the port itself would put
 * the "only FULL destinations count" rule on the web layer, and the second caller would then have
 * its own copy.
 *
 * <p>The filter itself is not applied here — {@link KnowledgePort#findSupportedDestinations()}
 * already returns only ranking-eligible rows, and {@code Destination.requireRankable} is where the
 * rule is asserted. This service exists for the resolution below, not to re-implement either.
 *
 * <h2>Why {@code ObjectProvider}</h2>
 *
 * <p>{@code KnowledgeRepositoryAdapter} is {@code @RequiresDatabase}, so in the database-less
 * context {@code ./gradlew test} starts — the {@code test} profile excludes
 * {@code DataSourceAutoConfiguration} outright — there is no {@link KnowledgePort} bean at all. A
 * constructor-injected port would fail context startup there and quietly give the unit suite a
 * Docker dependency. {@code SecurityConfig} resolves {@code UserRepositoryPort} the same way and
 * for the same reason.
 *
 * <p>The absent case answers "nothing is covered" rather than throwing. That is the honest reading:
 * a deployment with no knowledge base covers no destination, and it is the answer that keeps the
 * picker and the agent's refusal working — an exception here would turn a configuration gap into a
 * 500 on a public endpoint. It cannot silently reach production, because a real deployment has a
 * datasource and {@code KnowledgeConfigValidator} refuses to start one that is faking its corpus.
 */
@Service
public class SupportedDestinationService {

    private static final Logger log = LoggerFactory.getLogger(SupportedDestinationService.class);

    private final ObjectProvider<KnowledgePort> knowledge;
    private final boolean sampleSeedEnabled;

    public SupportedDestinationService(
            ObjectProvider<KnowledgePort> knowledge,
            @Value("${travelplanner.knowledge.sample-seed.enabled:false}") boolean sampleSeedEnabled) {
        this.knowledge = knowledge;
        this.sampleSeedEnabled = sampleSeedEnabled;
    }

    /**
     * ADR 010 §3 — whether this deployment's coverage list is backed by the sample seed.
     *
     * <p><strong>The deployment flag, not a per-row check.</strong> {@code Destination} carries no
     * provenance: it is the catalogue entry, and the citations live on the guide, POI and app rows
     * beneath it. Answering per-row here would mean a guide lookup per destination to compute one
     * boolean. The same property is what {@code KnowledgeConfigValidator} refuses to let a
     * production context start with, so both surfaces read one definition of "this corpus is
     * fabricated" rather than two that can disagree.
     */
    public boolean isSampleData() {
        return sampleSeedEnabled;
    }

    /** Every destination eligible for C2 ranking, in the port's stable order. Never null. */
    public List<Destination> listSupported() {
        KnowledgePort port = knowledge.getIfAvailable();
        if (port == null) {
            log.debug("No KnowledgePort is wired (no datasource); reporting zero supported destinations");
            return List.of();
        }
        return port.findSupportedDestinations();
    }
}
