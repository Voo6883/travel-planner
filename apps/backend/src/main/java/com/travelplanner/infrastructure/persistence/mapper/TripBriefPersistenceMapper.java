package com.travelplanner.infrastructure.persistence.mapper;

import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.valueobject.DateRange;
import com.travelplanner.domain.valueobject.Money;
import com.travelplanner.infrastructure.persistence.entity.TripBriefEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * {@link TripBriefEntity} ↔ {@link TripBrief}, including the two composite value objects.
 *
 * <p>This is the mapper the rest of the system copies. A value object spans several columns, and
 * both directions have to agree on what "absent" means: a {@code Money} with an amount but no
 * currency, or a {@code DateRange} with only a start, must be impossible on either side. The
 * column-pairing CHECK constraints in V6 enforce that in the database; the two assembly methods
 * below enforce it in Java, and both refuse to build half an object rather than defaulting the
 * missing half.
 */
@Mapper(config = PersistenceMapperConfig.class)
public interface TripBriefPersistenceMapper {

    @Mapping(target = "budget", source = "entity")
    @Mapping(target = "dates", source = "entity")
    TripBrief toDomain(TripBriefEntity entity);

    @Mapping(target = "budgetAmount", source = "budget.amount")
    @Mapping(target = "budgetCurrency", source = "budget.currency")
    @Mapping(target = "startDate", source = "dates.start")
    @Mapping(target = "endDate", source = "dates.end")
    void applyToEntity(TripBrief brief, @MappingTarget TripBriefEntity entity);

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

    /** {@link Currency} is not a persistable type; the column stores the ISO 4217 code. */
    default String toCurrencyCode(Currency currency) {
        return currency == null ? null : currency.getCurrencyCode();
    }
}
