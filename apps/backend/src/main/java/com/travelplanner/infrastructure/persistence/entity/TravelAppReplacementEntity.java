package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.AppReplacementReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row mapping for {@code travel_app_replacement} (V21).
 *
 * <p>{@code localAppId} is a plain {@code uuid} column rather than a {@code @ManyToOne} to
 * {@link TravelAppEntity}. The association is only ever navigated in the other direction — a caller
 * has a country's apps and wants each one's replacements — so an owning-side reference here would
 * add a lazy proxy nothing reads and one more thing for a {@code join fetch} to have to remember.
 * Same reasoning as {@code RefreshTokenEntity.userId}.
 *
 * <p>{@code replacedAppKey} is deliberately not a foreign key to anything: it names a globally-held
 * app that this market does not support, which is the opposite of what {@code travel_app} lists. The
 * V21 header explains the consequence of getting that wrong.
 *
 * <p>No {@code @Version}: the table has no {@code version} column. Curation is an authoring
 * operation, not a concurrent user edit.
 */
@Entity
@Table(name = "travel_app_replacement")
public class TravelAppReplacementEntity implements SourcedEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "local_app_id", nullable = false)
    private UUID localAppId;

    @Column(name = "replaced_app_key", nullable = false, length = 120)
    private String replacedAppKey;

    @Column(name = "replaced_app_name", nullable = false, length = 200)
    private String replacedAppName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private AppReplacementReason reason;

    /** {@code text} — the traveller-facing sentence. */
    @Column(name = "detail", nullable = false)
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private KnowledgeSourceEntity source;

    @Column(name = "retrieved_at", nullable = false)
    private Instant retrievedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mappers in the sibling package, which is why it is public. */
    public TravelAppReplacementEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getLocalAppId() {
        return localAppId;
    }

    public void setLocalAppId(UUID localAppId) {
        this.localAppId = localAppId;
    }

    public String getReplacedAppKey() {
        return replacedAppKey;
    }

    public void setReplacedAppKey(String replacedAppKey) {
        this.replacedAppKey = replacedAppKey;
    }

    public String getReplacedAppName() {
        return replacedAppName;
    }

    public void setReplacedAppName(String replacedAppName) {
        this.replacedAppName = replacedAppName;
    }

    public AppReplacementReason getReason() {
        return reason;
    }

    public void setReason(AppReplacementReason reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
