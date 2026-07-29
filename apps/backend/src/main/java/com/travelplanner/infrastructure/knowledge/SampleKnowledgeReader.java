package com.travelplanner.infrastructure.knowledge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.travelplanner.domain.enums.CoverageLevel;
import com.travelplanner.domain.enums.KnowledgeLicence;
import com.travelplanner.domain.enums.TrustTier;
import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.AreaNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PoiNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.PriceObservationNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.RouteSegmentNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.SeasonalityNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TransportModeNode;
import com.travelplanner.infrastructure.knowledge.SampleKnowledgeDocument.TravelAppNode;
import com.travelplanner.infrastructure.knowledge.SampleSourceDocument.SourceNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads and validates the sample seed files from the classpath.
 *
 * <h2>The validation is the point</h2>
 *
 * <p>Parsing JSON is the easy half. What this class exists for is to make ADR 010 §3 impossible to
 * violate by editing a data file: every node's {@code source_ref} must be the reserved
 * {@link KnowledgeProvenance#SAMPLE_SOURCE_REF}, the source itself must be
 * {@code SAMPLE_DATA}/{@code SAMPLE} with a {@code null} URL, and no destination may declare
 * {@code FULL} coverage. Those rules are also enforced by V13's CHECK constraints and by
 * {@code KnowledgeProvenance}'s own invariants, but a constraint violation two hundred rows into a
 * seed is a worse failure than a refusal to start: this one names the file and the offending value.
 *
 * <p>The {@code FULL} check is the one that would otherwise be silent. ADR 010 §4 admits only
 * {@code FULL} destinations to C2 ranking, so a sample file quietly promoted to {@code FULL} would
 * let fabricated content be ranked and presented as real — with no error anywhere.
 *
 * <h2>Its own {@link ObjectMapper}</h2>
 *
 * <p>Not the application's autoconfigured bean. The seed format is a file contract that tasks 40 and
 * 41 write against, and it must not change shape because somebody adjusted the HTTP wire format's
 * naming strategy in {@code application.yml}. Unknown properties are rejected and {@code null} for a
 * primitive is rejected, so a typo or an omitted {@code duration_minutes} fails loudly rather than
 * seeding a zero.
 */
public final class SampleKnowledgeReader {

    /** Classpath directory holding the seed files. */
    static final String BASE_PATH = "knowledge/sample/";

    /**
     * The three destinations ADR 010 §1 fixes for v1, as a compile-time constant rather than a
     * directory listing.
     *
     * <p>V18 creates one <em>partial</em> HNSW index per slug, so a fourth file dropped into the
     * directory would seed rows that no vector index covers and whose retrieval would silently
     * degrade to a sequential scan. Adding a destination is a migration first and a file second,
     * which is what this list makes visible.
     */
    static final List<String> DESTINATION_SLUGS = List.of("tokyo-jp", "bangkok-th", "shanghai-cn");

    private static final String SOURCES_FILE = "sources.json";

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /**
     * Reads {@code sources.json} and returns the reserved sample source, with {@code retrievedAt}
     * resolved.
     *
     * @param seededAt substituted when the file states no {@code retrieved_at}. Sample data was
     *     written rather than retrieved, so the seed run is the only honest fetch time — and it
     *     keeps ADR 010 §6's TTLs running from a real moment instead of a made-up one
     */
    public SourceNode readSampleSource(Instant seededAt) {
        SampleSourceDocument document = read(SOURCES_FILE, SampleSourceDocument.class);
        if (document.sources().size() != 1) {
            throw new IllegalStateException(SOURCES_FILE + " must declare exactly one source, the "
                    + "reserved " + KnowledgeProvenance.SAMPLE_SOURCE_REF + ", but declares "
                    + document.sources().size());
        }
        SourceNode declared = document.sources().get(0);
        SourceNode resolved = new SourceNode(
                declared.sourceRef(),
                declared.name(),
                declared.licence(),
                declared.attributionText(),
                declared.sourceUrl(),
                declared.retrievedAt() == null ? seededAt : declared.retrievedAt(),
                declared.trustTier());
        return requireReservedSampleSource(resolved);
    }

    /** Reads one destination file and validates every {@code source_ref} in it. */
    public SampleKnowledgeDocument readDestination(String slug) {
        String file = slug + ".json";
        SampleKnowledgeDocument document = read(file, SampleKnowledgeDocument.class);
        return requireSampleContent(document, file, slug);
    }

    /**
     * The ADR 010 §3 gate on the source itself.
     *
     * <p>Constructing a {@link KnowledgeProvenance} is deliberate rather than wasteful: it is where
     * "SAMPLE_DATA and TrustTier.SAMPLE imply each other" and "a forbidden licence may not be
     * persisted" already live, so this method does not restate them and cannot drift from them.
     */
    static SourceNode requireReservedSampleSource(SourceNode node) {
        if (!KnowledgeProvenance.SAMPLE_SOURCE_REF.equals(node.sourceRef())) {
            throw new IllegalStateException("Sample data may only cite the reserved source_ref '"
                    + KnowledgeProvenance.SAMPLE_SOURCE_REF + "' (ADR 010 §3), but " + SOURCES_FILE
                    + " declares '" + node.sourceRef() + "'");
        }
        if (node.sourceUrl() != null) {
            // The rule this enforces is the whole reason the reserved ref exists: a stub row that
            // cites a plausible URL is fabricated provenance, and nothing downstream can tell.
            throw new IllegalStateException("The reserved sample source must have no source_url "
                    + "(ADR 010 §3 forbids a plausible-looking URL), but found '"
                    + node.sourceUrl() + "'");
        }
        if (node.licence() != KnowledgeLicence.SAMPLE_DATA || node.trustTier() != TrustTier.SAMPLE) {
            throw new IllegalStateException("The reserved sample source must be SAMPLE_DATA/SAMPLE, "
                    + "but found " + node.licence() + "/" + node.trustTier());
        }
        // Runs KnowledgeProvenance's invariants over the values about to be persisted.
        new KnowledgeProvenance(node.sourceRef(), node.name(), node.licence(),
                node.attributionText(), node.sourceUrl(), node.trustTier(), node.retrievedAt());
        return node;
    }

    /** Every citation in a sample destination file, checked before a single row is written. */
    static SampleKnowledgeDocument requireSampleContent(
            SampleKnowledgeDocument document, String file, String expectedSlug) {

        if (!expectedSlug.equals(document.destination().slug())) {
            throw new IllegalStateException(file + " declares slug '" + document.destination().slug()
                    + "', which does not match its file name");
        }
        if (document.destination().coverageLevel() == CoverageLevel.FULL) {
            throw new IllegalStateException(file + " declares coverage_level FULL. Sample data is "
                    + "not curated depth, and ADR 010 §4 admits only FULL destinations to C2 "
                    + "ranking — marking it FULL would let sample content be ranked and presented "
                    + "as real. Use PARTIAL.");
        }
        requireSampleRef(document.guide().sourceRef(), file, "guide");
        for (AreaNode area : document.areas()) {
            requireSampleRef(area.sourceRef(), file, "area " + area.slug());
        }
        for (PoiNode poi : document.pois()) {
            requireSampleRef(poi.sourceRef(), file, "poi " + poi.slug());
        }
        for (TransportModeNode mode : document.transportModes()) {
            requireSampleRef(mode.sourceRef(), file, "transport mode " + mode.slug());
        }
        for (RouteSegmentNode segment : document.routeSegments()) {
            requireSampleRef(segment.sourceRef(), file,
                    "route segment " + segment.fromAreaSlug() + " -> " + segment.toAreaSlug());
        }
        for (SeasonalityNode month : document.seasonality()) {
            requireSampleRef(month.sourceRef(), file, "seasonality month " + month.month());
        }
        for (PriceObservationNode price : document.priceHistory()) {
            requireSampleRef(price.sourceRef(), file, "price " + price.category());
        }
        for (TravelAppNode app : document.travelApps()) {
            requireSampleRef(app.sourceRef(), file, "travel app " + app.slug());
        }
        return document;
    }

    private static void requireSampleRef(String sourceRef, String file, String where) {
        if (!KnowledgeProvenance.SAMPLE_SOURCE_REF.equals(sourceRef)) {
            throw new IllegalStateException(file + ": " + where + " cites source_ref '" + sourceRef
                    + "'. Every sample row must cite the reserved '"
                    + KnowledgeProvenance.SAMPLE_SOURCE_REF + "' (ADR 010 §3).");
        }
    }

    private <T> T read(String fileName, Class<T> type) {
        ClassPathResource resource = new ClassPathResource(BASE_PATH + fileName);
        try (InputStream stream = resource.getInputStream()) {
            return mapper.readValue(stream, type);
        } catch (IOException failure) {
            throw new UncheckedIOException(
                    "Could not read sample knowledge file " + BASE_PATH + fileName, failure);
        }
    }
}
