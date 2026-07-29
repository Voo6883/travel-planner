package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.enums.TravelInterest;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.domain.valueobject.PartySize;
import com.travelplanner.infrastructure.persistence.entity.TripBriefEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * {@link TripBriefEntity} ↔ {@link TripBrief}, including the three composite value objects.
 *
 * <p>This is the mapper the rest of the system copies. A value object spans several columns, and
 * both directions have to agree on what "absent" means: a {@code Money} with an amount but no
 * currency, a {@code DateRange} with only a start, or a {@code PartySize} with adults but no child
 * count must be impossible on either side. The column-pairing CHECK constraints in V6 and V20
 * enforce that in the database; the assembly methods below enforce it in Java, and all of them
 * refuse to build half an object rather than defaulting the missing half.
 *
 * <p>The {@code PartySize} halves are written in {@link #applyParty} rather than through nested
 * {@code source = "party.adults"} mappings. Its components are primitive {@code int}, so a nested
 * mapping onto a nullable column would have to decide what an absent party means for a primitive —
 * and the answer it would reach for, {@code 0}, is a party with no adults, which the domain refuses
 * to construct. Writing both halves in one place keeps "absent" meaning absent.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface TripBriefPersistenceMapper {

    @Mapping(target = "budget", source = "entity")
    @Mapping(target = "dates", source = "entity")
    @Mapping(target = "party", source = "entity")
    TripBrief toDomain(TripBriefEntity entity);

    @Mapping(target = "budgetAmount", source = "budget.amount")
    @Mapping(target = "budgetCurrency", source = "budget.currency")
    @Mapping(target = "startDate", source = "dates.start")
    @Mapping(target = "endDate", source = "dates.end")
    @Mapping(target = "partyAdults", ignore = true)
    @Mapping(target = "partyChildren", ignore = true)
    void applyToEntity(TripBrief brief, @MappingTarget TripBriefEntity entity);

    /** Both halves of the party, or neither. See the class note on why this is not a nested mapping. */
    @AfterMapping
    default void applyParty(TripBrief brief, @MappingTarget TripBriefEntity entity) {
        PartySize party = brief.party();
        entity.setPartyAdults(party == null ? null : party.adults());
        entity.setPartyChildren(party == null ? null : party.children());
    }

    /**
     * Reassembles {@code (budget_amount, budget_currency)}. Absent when both are null; a row where
     * exactly one is null cannot exist ({@code ck_trip_brief_budget_paired}), so the pairing is a
     * genuine invariant rather than a defensive guess.
     */
    default Money toMoney(TripBriefEntity entity) {
        BigDecimal amount = entity.getBudgetAmount();
        String currencyCode = entity.getBudgetCurrency();
        if (amount == null || currencyCode == null) {
            return null;
        }
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    /** Reassembles {@code (start_date, end_date)}. Paired by {@code ck_trip_brief_dates_paired}. */
    default DateRange toDateRange(TripBriefEntity entity) {
        LocalDate start = entity.getStartDate();
        LocalDate end = entity.getEndDate();
        if (start == null || end == null) {
            return null;
        }
        return new DateRange(start, end);
    }

    /** Reassembles {@code (party_adults, party_children)}. Paired by {@code ck_trip_brief_party_paired}. */
    default PartySize toPartySize(TripBriefEntity entity) {
        Integer adults = entity.getPartyAdults();
        Integer children = entity.getPartyChildren();
        if (adults == null || children == null) {
            return null;
        }
        return new PartySize(adults, children);
    }

    /**
     * {@code text[]} of enum names → constants. An unrecognised name throws rather than being
     * skipped: a brief that quietly lost an interest would be ranked against a preference set the
     * user never edited, and C2 would then explain its reasoning in terms of one that did not exist.
     */
    default List<TravelInterest> toInterests(List<String> names) {
        return names == null ? List.of() : names.stream().map(TravelInterest::valueOf).toList();
    }

    /**
     * Constants → {@code text[]} of enum names. Never null, because the column is {@code NOT NULL},
     * and deliberately mutable: MapStruct assigns this straight onto the entity, and Hibernate's
     * array binding copies out of whatever list it is handed — an immutable one works today and
     * would break the moment the mapping grew a {@code clear()}/{@code addAll()} branch, which is
     * exactly what MapStruct emits when the target list is already populated.
     */
    default List<String> toInterestNames(List<TravelInterest> interests) {
        List<String> names = new ArrayList<>();
        if (interests != null) {
            interests.forEach(interest -> names.add(interest.name()));
        }
        return names;
    }

    /** {@link Currency} is not a persistable type; the column stores the ISO 4217 code. */
    default String toCurrencyCode(Currency currency) {
        return currency == null ? null : currency.getCurrencyCode();
    }
}
