package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.PoiCategory;
import com.travelplanner.domain.enums.PriceBand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code poi} (V15).
 *
 * <p>Carries the shortest TTL in the TKB — 90 days on {@code openingHours} and {@code priceBand},
 * per ADR 010 §6 — which is why {@link #retrievedAt} is denormalised onto the row rather than left
 * on the source: staleness has to be answerable from the row being returned, not from a join the
 * caller might omit.
 *
 * <p>{@code areaId} is nullable and stays a raw {@link UUID}. Not every POI sits inside a curated
 * area, and the FK is {@code ON DELETE SET NULL} because removing an area must not delete the
 * restaurants in it.
 *
 * <p>{@code version} is a primitive {@code int} for the reason {@code TripEntity} documents — a
 * nullable version would make Spring Data alternate between {@code persist} and {@code merge}
 * depending on field state, which is how duplicate-insert bugs start.
 */
@Entity
@Table(name = "poi")
public class PoiEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "slug", nullable = false, length = 160)
    private String slug;

    @Column(name = "name", nullable = false, length = 300)
    private String name;

    /** {@code text}. Embedded together with the name and tags as one chunk (ADR 010 §5). */
    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 64)
    private PoiCategory category;

    /**
     * Postgres {@code text[]}, mapped by Hibernate 6's native array support rather than by a
     * converter or a join table.
     *
     * <p>{@code @JdbcTypeCode(ARRAY)} is what makes the {@code List<String>} bind as a real SQL
     * array; without it Hibernate treats the collection as an element collection and looks for a
     * table that does not exist. A converter serialising to a delimited string would work until the
     * first tag containing the delimiter, and would make the column unreadable from SQL.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false)
    private List<String> tags;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /**
     * Free text on purpose. Real hours are irregular ("closed 2nd Tuesday, 11:00-14:30 in winter")
     * and a structured model that cannot express the exception invites a confident wrong answer.
     */
    @Column(name = "opening_hours", length = 500)
    private String openingHours;

    /** Ordinal band, never an amount — {@link PriceHistoryEntity} is where money lives. */
    @Enumerated(EnumType.STRING)
    @Column(name = "price_band", length = 16)
    private PriceBand priceBand;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public PoiEntity() {
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

    public UUID getAreaId() {
        return areaId;
    }

    public void setAreaId(UUID areaId) {
        this.areaId = areaId;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PoiCategory getCategory() {
        return category;
    }

    public void setCategory(PoiCategory category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public PriceBand getPriceBand() {
        return priceBand;
    }

    public void setPriceBand(PriceBand priceBand) {
        this.priceBand = priceBand;
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

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
