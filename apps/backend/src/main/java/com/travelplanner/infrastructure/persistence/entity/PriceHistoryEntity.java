package com.travelplanner.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code price_history} (V17) — the only place in the TKB holding actual money.
 *
 * <p>{@code amount} and {@code currency} stay two fields, exactly as {@code TripBriefEntity} keeps
 * its budget: {@code Money} is a domain value object, and giving the entity one would either need an
 * {@code @Embeddable} duplicating the domain type or a converter that hides the currency inside a
 * string. The mapper reassembles them, and refuses to build half a {@code Money}.
 *
 * <p>{@code observedOn} is a {@link LocalDate}, not an {@link Instant}. It is the calendar month the
 * figure describes rather than a moment, and {@code ck_price_history_observed_on_first_of_month}
 * pins it to the first — which is what makes "one observation per month" enforceable at all.
 *
 * <p>No {@code updated_at} column and no {@code @Version}: an observation is a fact about a month
 * that has already happened. A correction replaces the row rather than editing it in place, so
 * there is nothing for optimistic locking to arbitrate.
 */
@Entity
@Table(name = "price_history")
public class PriceHistoryEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    /**
     * Free text ({@code HOTEL_NIGHT}, {@code MEAL_MID_RANGE}, ...) rather than an enum: a closed
     * vocabulary would need a migration every time curation learns a new category, and
     * {@code uq_price_history_observation} supplies the discipline instead.
     */
    @Column(name = "category", nullable = false, length = 64)
    private String category;

    /** {@code numeric(12,2)} — money is never a floating-point type (PLAN §13.1). */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** {@code char(3)} — see {@link DestinationEntity#getCountryCode} on why the type code matters. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public PriceHistoryEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(UUID destinationId) {
        this.destinationId = destinationId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getObservedOn() {
        return observedOn;
    }

    public void setObservedOn(LocalDate observedOn) {
        this.observedOn = observedOn;
    }

    @Override
    public KnowledgeSourceEntity getSource() {
        return source;
    }

    public void setSource(KnowledgeSourceEntity source) {
        this.source = source;
    }

    @Override
    public Instant getRetrievedAt() {
        return retrievedAt;
    }

    public void setRetrievedAt(Instant retrievedAt) {
        this.retrievedAt = retrievedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
