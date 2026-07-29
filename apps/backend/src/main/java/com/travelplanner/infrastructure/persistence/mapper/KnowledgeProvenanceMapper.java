package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.valueobject.KnowledgeProvenance;
import com.travelplanner.infrastructure.persistence.entity.SourcedEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Builds a {@link KnowledgeProvenance} from a catalogue row and the source it cites.
 *
 * <p>Every knowledge mapper delegates here through {@code uses}, because the assembly reads from
 * <em>two</em> places and only one of them is obvious.
 *
 * <p><strong>{@code retrievedAt} comes from the row, never from the source.</strong> Both
 * {@code knowledge_source} and every catalogue table have a column by that name, and they mean
 * different things: the source's records when that source was last fetched, the row's records when
 * <em>this fact</em> was taken from it. ADR 010 §6 measures its TTLs — 90 days on POI details, 180
 * on app links, 365 on seasonality and prices, 730 on narrative — against the row. Reading the
 * source's instead would make a re-fetched source silently rejuvenate every fact ever taken from
 * it, and staleness would be wrong for the whole catalogue with nothing failing to say so. The
 * explicit {@code @Mapping} below is what pins that down; without it MapStruct would still compile,
 * having quietly picked one of the two.
 *
 * <p>The parameter is {@link SourcedEntity} rather than nine overloads. MapStruct selects a mapping
 * method whose parameter type the source is assignable to, so one method covers every catalogue
 * entity and the rule above is stated exactly once.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface KnowledgeProvenanceMapper {

    @Mapping(target = "sourceRef", source = "source.sourceRef")
    @Mapping(target = "name", source = "source.name")
    @Mapping(target = "licence", source = "source.licence")
    @Mapping(target = "attributionText", source = "source.attributionText")
    @Mapping(target = "sourceUrl", source = "source.sourceUrl")
    @Mapping(target = "trustTier", source = "source.trustTier")
    // The row's own fetch time. See the class javadoc — this is the line that must not become
    // "source.retrievedAt".
    @Mapping(target = "retrievedAt", source = "retrievedAt")
    KnowledgeProvenance toProvenance(SourcedEntity entity);
}
