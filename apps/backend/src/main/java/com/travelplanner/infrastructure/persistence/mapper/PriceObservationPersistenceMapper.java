package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.PriceObservation;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.infrastructure.persistence.entity.PriceHistoryEntity;
import java.util.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@link PriceHistoryEntity} → {@link PriceObservation}.
 *
 * <p>Read direction only — see {@link DestinationPersistenceMapper} for why.
 *
 * <p>The second hand-written assembly in this package, after the provenance itself: {@code amount}
 * spans two columns. It follows {@code TripBriefPersistenceMapper} exactly, which is the point —
 * there is one {@link Money} type in the system and one way of rebuilding it, so a second money-ish
 * shape cannot appear here and start losing its currency.
 *
 * <p>Both columns are NOT NULL, so unlike the trip brief's optional budget there is no "absent"
 * case: a row missing either half is a corrupt row, and {@link Currency#getInstance(String)} failing
 * loudly is the right outcome. {@code Money} then normalises the scale to the currency's minor units
 * — {@code numeric(12,2)} reads back as {@code 5000.00}, which for a zero-decimal currency such as
 * JPY is exact and rescales cleanly, while a genuinely over-precise figure is rejected rather than
 * rounded.
 */
@Mapper(config = PersistenceMapperConfig.class, uses = KnowledgeProvenanceMapper.class)
public interface PriceObservationPersistenceMapper {

    @Mapping(target = "amount", source = "entity")
    @Mapping(target = "provenance", source = "entity")
    PriceObservation toDomain(PriceHistoryEntity entity);

    /** Reassembles {@code (amount, currency)}. Both NOT NULL, so there is no half-set case. */
    default Money toMoney(PriceHistoryEntity entity) {
        return new Money(entity.getAmount(), Currency.getInstance(entity.getCurrency()));
    }
}
