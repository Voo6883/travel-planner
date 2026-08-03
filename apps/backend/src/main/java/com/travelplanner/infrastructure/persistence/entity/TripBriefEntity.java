package com.travelplanner.infrastructure.persistence.entity;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelPace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Row mapping for {@code trip_brief} (V6).
 *
 * <p>Money is a {@link BigDecimal} amount plus an ISO 4217 code in two columns, reassembled into a
 * {@code Money} by the mapper. Two columns rather than one because "4000" without a currency is not
 * an amount, and the pairing is enforced by {@code ck_trip_brief_budget_paired} so the halves can
 * never drift apart through a direct SQL write.
 *
 * <p>{@code destinations} and {@code interests} are Postgres {@code text[]}, mapped by Hibernate 6's
 * native array support exactly as {@code PoiEntity.tags} is. {@code @JdbcTypeCode(ARRAY)} is what
 * makes the {@code List<String>} bind as a real SQL array — without it Hibernate treats the
 * collection as an element collection and looks for a join table that does not exist.
 *
 * <p>{@code interests} is {@code List<String>} rather than {@code List<TravelInterest>}: Hibernate's
 * array support has no element converter, so the enum names are stored as text and the mapper turns
 * them back into constants. That is also where an unknown name surfaces, which is the right place —
 * a value the domain has no constant for is a mapping failure, not a silently dropped interest.
 */
@Entity
@Table(name = "trip_brief")
public class TripBriefEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "budget_amount", precision = 19, scale = 4)
    private BigDecimal budgetAmount;

    @Column(name = "budget_currency", length = 3)
    private String budgetCurrency;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** Destination slugs, most-preferred first. Empty means "no preference", never "unknown". */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "destinations", nullable = false)
    private List<String> destinations;

    @Column(name = "surprise_me", nullable = false)
    private boolean surpriseMe;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_flexibility", length = 32)
    private DateFlexibility dateFlexibility;

    @Column(name = "departure_city", length = 120)
    private String departureCity;

    /** Half a party is not a party — {@code ck_trip_brief_party_paired} enforces the pairing. */
    @Column(name = "party_adults")
    private Integer partyAdults;

    @Column(name = "party_children")
    private Integer partyChildren;

    /** {@code TravelInterest} names; reassembled into constants by the mapper. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "interests", nullable = false)
    private List<String> interests;

    @Enumerated(EnumType.STRING)
    @Column(name = "pace", length = 16)
    private TravelPace pace;

    /** ADR 008 §1. */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA, and by the mapper in the sibling package, which is why it is public. */
    public TripBriefEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public String getBudgetCurrency() {
        return budgetCurrency;
    }

    public void setBudgetCurrency(String budgetCurrency) {
        this.budgetCurrency = budgetCurrency;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<String> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<String> destinations) {
        this.destinations = destinations;
    }

    public boolean isSurpriseMe() {
        return surpriseMe;
    }

    public void setSurpriseMe(boolean surpriseMe) {
        this.surpriseMe = surpriseMe;
    }

    public DateFlexibility getDateFlexibility() {
        return dateFlexibility;
    }

    public void setDateFlexibility(DateFlexibility dateFlexibility) {
        this.dateFlexibility = dateFlexibility;
    }

    public String getDepartureCity() {
        return departureCity;
    }

    public void setDepartureCity(String departureCity) {
        this.departureCity = departureCity;
    }

    public Integer getPartyAdults() {
        return partyAdults;
    }

    public void setPartyAdults(Integer partyAdults) {
        this.partyAdults = partyAdults;
    }

    public Integer getPartyChildren() {
        return partyChildren;
    }

    public void setPartyChildren(Integer partyChildren) {
        this.partyChildren = partyChildren;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public TravelPace getPace() {
        return pace;
    }

    public void setPace(TravelPace pace) {
        this.pace = pace;
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
