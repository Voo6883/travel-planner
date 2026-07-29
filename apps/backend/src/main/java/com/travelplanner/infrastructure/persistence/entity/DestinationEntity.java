package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.CoverageLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code destination} (V14).
 *
 * <p>The only catalogue table with no {@code source_id}, and therefore the only one that does not
 * implement {@link SourcedEntity}. A destination is an identifier for a place rather than a claim
 * about it — the claims live in {@code destination_guide}, {@code poi} and the rest, each with its
 * own citation. Adding provenance here would invite a caller to attribute a guide's licence to the
 * city's name.
 *
 * <p>{@code coverageLevel} is not a status flag to be ignored. ADR 010 §4 makes only {@code FULL}
 * destinations rankable, and {@code Destination.requireRankable} is where that is enforced; the
 * column exists so the repository can list candidates without the domain having to guess.
 *
 * <p>No {@code @Version}: the table has no {@code version} column.
 */
@Entity
@Table(name = "destination")
public class DestinationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * {@code char(2)}, not {@code varchar}. ISO 3166-1 alpha-2 is exactly two characters, and the
     * {@code @JdbcTypeCode} is what stops {@code ddl-auto: validate} rejecting the column as a
     * {@code varchar} mismatch — the same treatment {@link AiCallLogEntity} needs for its currency.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    /** IANA zone. A property of the place, not of a trip — task 28 puts events on this clock. */
    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    /**
     * {@code numeric(9,6)} rather than {@code double}.
     *
     * <p>The domain exposes {@link Double} because a coordinate is used for distance arithmetic, but
     * the entity must not: a {@code double} field would round-trip the stored value through binary
     * floating point and change its scale, so the row read back would not equal the row written. The
     * mapper narrows; the column keeps its declared precision.
     */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    /** Paired with {@link #latitude} by {@code ck_destination_coordinates_paired}. */
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_level", nullable = false, length = 16)
    private CoverageLevel coverageLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public DestinationEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
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

    public CoverageLevel getCoverageLevel() {
        return coverageLevel;
    }

    public void setCoverageLevel(CoverageLevel coverageLevel) {
        this.coverageLevel = coverageLevel;
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
