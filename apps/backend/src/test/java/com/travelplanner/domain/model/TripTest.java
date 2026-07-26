package com.travelplanner.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.ValidationFailedException;
import com.travelplanner.domain.exception.VersionConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure domain test — no Spring context (PLAN §4.0.2-K). */
class TripTest {

    private static final Instant NOW = Instant.parse("2026-04-01T09:00:00Z");
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void aNewTripStartsAsADraftAtVersionZero() {
        Trip trip = Trip.create(OWNER, "Japan in spring", NOW);

        assertThat(trip.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(trip.version()).isZero();
        assertThat(trip.id()).isNotNull();
        assertThat(trip.createdAt()).isEqualTo(NOW);
        assertThat(trip.selectedRecommendation()).isEmpty();
    }

    @Test
    void trimsAndValidatesTheName() {
        assertThat(Trip.create(OWNER, "  Kyoto  ", NOW).name()).isEqualTo("Kyoto");

        assertThatThrownBy(() -> Trip.create(OWNER, "   ", NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> Trip.create(OWNER, "x".repeat(Trip.MAX_NAME_LENGTH + 1), NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void isOwnedByAnswersTheUserScopingQuestion() {
        Trip trip = Trip.create(OWNER, "Kyoto", NOW);

        assertThat(trip.isOwnedBy(OWNER)).isTrue();
        assertThat(trip.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void anArchivedTripRefusesEveryMutation() {
        Trip archived = Trip.create(OWNER, "Kyoto", NOW).withStatus(TripStatus.ARCHIVED, NOW);

        assertThat(archived.status().isReadOnly()).isTrue();
        assertThatThrownBy(() -> archived.rename("Osaka", NOW))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> archived.withStatus(TripStatus.DRAFT, NOW))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void aStatusChangeProducesANewInstanceAndLeavesTheOriginalUntouched() {
        Trip draft = Trip.create(OWNER, "Kyoto", NOW);
        Instant later = NOW.plusSeconds(60);

        Trip complete = draft.withStatus(TripStatus.BRIEF_COMPLETE, later);

        assertThat(draft.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(complete.status()).isEqualTo(TripStatus.BRIEF_COMPLETE);
        assertThat(complete.updatedAt()).isEqualTo(later);
        assertThat(complete.createdAt()).isEqualTo(NOW);
    }

    @Test
    void requireVersionReportsTheCurrentVersionSoTheLoserCanRecover() {
        Trip stored = new Trip(UUID.randomUUID(), OWNER, "Kyoto", TripStatus.DRAFT, null, 9, NOW, NOW);

        assertThatThrownBy(() -> Versioned.requireVersion(stored, 7))
                .isInstanceOf(VersionConflictException.class)
                .extracting(conflict -> ((VersionConflictException) conflict).details())
                .isEqualTo(java.util.Map.of("current_version", 9));
    }

    @Test
    void requireVersionPassesWhenTheClientIsUpToDate() {
        Trip stored = new Trip(UUID.randomUUID(), OWNER, "Kyoto", TripStatus.DRAFT, null, 9, NOW, NOW);

        Versioned.requireVersion(stored, 9);
    }
}
