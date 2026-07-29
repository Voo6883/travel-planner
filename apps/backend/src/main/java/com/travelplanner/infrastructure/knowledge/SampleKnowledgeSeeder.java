package com.travelplanner.infrastructure.knowledge;

import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeWriter.SeedCounts;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Loads the sample Travel Knowledge Base on startup (task 17, ADR 010 §3).
 *
 * <h2>This seeds SAMPLE data, not curated facts</h2>
 *
 * <p>ADR 010 §3 permits sample knowledge in development only when it is unmistakably marked, and the
 * marking is not cosmetic — it is what keeps the product's first promise, "never invent facts". Every
 * row this class writes cites the single reserved source {@link KnowledgeProvenance#SAMPLE_SOURCE_REF}
 * with {@code licence = SAMPLE_DATA}, {@code trust_tier = SAMPLE} and no {@code source_url}. Nothing
 * in {@code classpath:knowledge/sample/} was taken from a publisher, and
 * {@link SampleKnowledgeReader} refuses the whole seed if any node claims otherwise.
 *
 * <h2>Why the destinations are seeded {@code PARTIAL} and never {@code FULL}</h2>
 *
 * <p>This is the decision most worth understanding before changing anything here.
 *
 * <p>ADR 010 §4 makes {@code coverage_level} the gate on C2 ranking: <em>only</em> {@code FULL}
 * destinations are ranked, and a request naming anything else returns a typed
 * {@code destination_not_covered} with the supported list. Marking sample rows {@code FULL} would
 * therefore put invented POIs, invented bands and placeholder prices into the ranking pipeline and
 * present them to a traveller as real — with nothing failing, because a fabricated row scores exactly
 * like a sourced one.
 *
 * <p>The sample set is also honestly short of ADR 010 §1's curated depth (10 POIs per destination
 * against a floor of 25), so {@code FULL} would be a false claim about the data even setting the
 * ranking consequence aside. {@code PARTIAL} says the true thing: rows exist, they are retrievable
 * and citable, and they are not fit to rank.
 *
 * <h2>The two gates</h2>
 *
 * <p><strong>Profile.</strong> {@code @Profile({"dev", "docker", "local"})}, copied from
 * {@code DevAdminSeeder}. It is an <em>allow-list</em>, and that is the whole point: a deny-list on
 * {@code prod} silently admits every profile somebody invents later — {@code staging},
 * {@code preview}, {@code demo} — each of which is an environment where sample travel data could be
 * shown to a real person. An allow-list admits nothing until a human adds it. Under {@code prod} the
 * bean is not instantiated at all, so there is no runtime flag to disarm and no configuration string
 * that can be got wrong.
 *
 * <p><strong>Property.</strong> {@code travelplanner.knowledge.sample-seed.enabled}, absent by
 * default. Even in development, sample rows appear because a developer asked for them.
 *
 * <h2>Idempotence</h2>
 *
 * <p>Re-running creates nothing that already exists and updates nothing that does — see
 * {@link SampleKnowledgeWriter}. Embeddings are re-checked against their {@code content_hash} on
 * every run and re-embedded only on a mismatch (ADR 010 §5).
 */
@Component
@Profile({"dev", "docker", "local"})
@ConditionalOnProperty(
        prefix = "travelplanner.knowledge.sample-seed", name = "enabled", havingValue = "true")
@RequiresDatabase
public class SampleKnowledgeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleKnowledgeSeeder.class);

    /** The configuration key both this class and its collaborators are gated on. */
    public static final String ENABLED_PROPERTY = "travelplanner.knowledge.sample-seed.enabled";

    private final SampleKnowledgeWriter writer;
    private final SampleKnowledgeReader reader = new SampleKnowledgeReader();

    /**
     * A field rather than a shared bean, matching {@code AiConfig}: no other package publishes a
     * {@code Clock}, and introducing one for a startup seed would be a wider change than the seed.
     */
    private final Clock clock = Clock.systemUTC();

    public SampleKnowledgeSeeder(SampleKnowledgeWriter writer) {
        this.writer = writer;
    }

    /**
     * Runs after the context refreshes, and therefore after Flyway has migrated — V13 to V18 have to
     * exist before a single row can be written, and {@code ApplicationRunner} is the documented point
     * at which they do.
     */
    @Override
    public void run(ApplicationArguments arguments) {
        seed();
    }

    /** @return the total number of catalogue rows created and chunks embedded by this call */
    public SeedCounts seed() {
        Instant now = clock.instant();
        // Warn, not info. A developer skimming a startup log has to be able to see that the
        // knowledge base in front of them is invented, without going looking for it.
        log.warn("Seeding SAMPLE travel knowledge from classpath:{} — every row cites '{}' and is "
                        + "NOT sourced, verified, or fit to show a traveller. Destinations are "
                        + "seeded PARTIAL so ADR 010 §4 keeps them out of ranking.",
                SampleKnowledgeReader.BASE_PATH, KnowledgeProvenance.SAMPLE_SOURCE_REF);

        if (writer.ensureSampleSource(reader.readSampleSource(now), now)) {
            log.info("Created the reserved sample knowledge source '{}'",
                    KnowledgeProvenance.SAMPLE_SOURCE_REF);
        }

        int rows = 0;
        int chunks = 0;
        for (String slug : SampleKnowledgeReader.DESTINATION_SLUGS) {
            SeedCounts counts = writer.seed(reader.readDestination(slug), now);
            rows += counts.rowsCreated();
            chunks += counts.chunksEmbedded();
            log.info("Sample knowledge for '{}': {} rows created, {} chunks embedded",
                    slug, counts.rowsCreated(), counts.chunksEmbedded());
        }

        if (rows == 0 && chunks == 0) {
            log.info("Sample travel knowledge already present and unchanged — nothing to do");
        } else {
            log.warn("Seeded SAMPLE travel knowledge: {} rows created, {} chunks embedded across {} "
                            + "destinations. This data is illustrative only.",
                    rows, chunks, SampleKnowledgeReader.DESTINATION_SLUGS.size());
        }
        return new SeedCounts(rows, chunks);
    }
}
