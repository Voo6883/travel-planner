package com.travelplanner.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelplanner.application.trip.ArchiveTripCommand;
import com.travelplanner.application.trip.CreateTripCommand;
import com.travelplanner.application.trip.RenameTripCommand;
import com.travelplanner.application.trip.TripService;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.exception.VersionConflictException;
import com.travelplanner.domain.model.Trip;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The trip routing contract: snake_case wire names, the versioned request bodies ADR 008 requires,
 * and the error envelope each refusal maps to.
 *
 * <p>{@code addFilters = false} for the reason {@code DestinationControllerTest} gives: a
 * {@code @WebMvcTest} does not load the application's own {@code SecurityConfig}, so Boot's default
 * "authenticate everything" chain would turn every routing assertion into a 401. That these paths
 * really do require a session is a property of {@code SecurityConfig}'s
 * {@code anyRequest().authenticated()} and is asserted against the real configuration by the API
 * integration suite.
 *
 * <p>With the filters off there is no {@code SecurityContext}, so the caller resolves to
 * {@code null} and reaches a mock. That is what makes the {@code verify} assertions below readable
 * — they pin the command the controller built, which is the only thing this layer decides.
 *
 * <p>{@code spring.datasource.url} is set only to satisfy {@code @RequiresDatabase} on the
 * controller. Nothing here opens a connection: {@code @WebMvcTest} does not autoconfigure a
 * {@code DataSource}, and the service is a mock.
 */
@WebMvcTest(controllers = TripController.class,
        properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/unused")
@AutoConfigureMockMvc(addFilters = false)
class TripControllerTest {

    private static final UUID TRIP_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    private static final Trip KYOTO = new Trip(TRIP_ID, UUID.randomUUID(), "Kyoto",
            TripStatus.DRAFT, null, 3, NOW, NOW);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TripService trips;

    @Test
    void theListIsWrappedInAnObjectAndPublishesEveryTripsVersion() throws Exception {
        // A bare array cannot gain a field; the version is what a client's next write must echo.
        when(trips.list(any())).thenReturn(List.of(KYOTO));

        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trips[0].trip_id").value(TRIP_ID.toString()))
                .andExpect(jsonPath("$.trips[0].name").value("Kyoto"))
                .andExpect(jsonPath("$.trips[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.trips[0].version").value(3));
    }

    @Test
    void theListNeverPublishesTheOwnersUserId() throws Exception {
        // Ownership comes from the session on every request; echoing it back would publish an
        // identifier no endpoint on this API accepts as input.
        when(trips.list(any())).thenReturn(List.of(KYOTO));

        mockMvc.perform(get("/api/v1/trips"))
                .andExpect(jsonPath("$.trips[0].user_id").doesNotExist())
                .andExpect(jsonPath("$.trips[0].userId").doesNotExist());
    }

    @Test
    void creatingATripAnswersTwoZeroOneWithTheNewResource() throws Exception {
        when(trips.create(any(CreateTripCommand.class), any())).thenReturn(KYOTO);

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kyoto\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void aBlankNameIsRejectedBeforeTheServiceIsCalled() throws Exception {
        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"));
    }

    @Test
    void aRenameWithoutAnExpectedVersionIsValidationFailedAndNeverAForceOverwrite() throws Exception {
        // ADR 008 §2. A primitive field would have bound the missing value to 0 and silently
        // overwritten whatever the agent had just written.
        mockMvc.perform(put("/api/v1/trips/" + TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kyoto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_failed"))
                .andExpect(jsonPath("$.details.fields.expected_version").exists());
    }

    @Test
    void aRenameEchoesTheVersionFromTheBodyAndReturnsTheIncrementedResource() throws Exception {
        Trip renamed = new Trip(TRIP_ID, KYOTO.userId(), "Kyoto and Osaka", TripStatus.DRAFT,
                null, 4, NOW, NOW);
        when(trips.rename(any(RenameTripCommand.class), any())).thenReturn(renamed);

        mockMvc.perform(put("/api/v1/trips/" + TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":3,\"name\":\"Kyoto and Osaka\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kyoto and Osaka"))
                .andExpect(jsonPath("$.version").value(4));

        verify(trips).rename(new RenameTripCommand(TRIP_ID, 3, "Kyoto and Osaka"), null);
    }

    @Test
    void aStaleVersionIsFourZeroNineCarryingTheCurrentOne() throws Exception {
        // The shape ADR 008 §2 fixes: the loser has to be able to re-read at current_version
        // rather than blindly refetch.
        when(trips.rename(any(RenameTripCommand.class), any()))
                .thenThrow(new VersionConflictException(9));

        mockMvc.perform(put("/api/v1/trips/" + TRIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":3,\"name\":\"Kyoto\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andExpect(jsonPath("$.details.current_version").value(9));
    }

    @Test
    void archivingIsATypedActionCarryingOnlyTheVersion() throws Exception {
        Trip archived = new Trip(TRIP_ID, KYOTO.userId(), "Kyoto", TripStatus.ARCHIVED, null,
                4, NOW, NOW);
        when(trips.archive(any(ArchiveTripCommand.class), any())).thenReturn(archived);

        mockMvc.perform(post("/api/v1/trips/" + TRIP_ID + "/actions/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expected_version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(trips).archive(new ArchiveTripCommand(TRIP_ID, 3), null);
    }

    @Test
    void anotherUsersTripIsFourZeroFourRatherThanForbidden() throws Exception {
        // A 403 would confirm that the trip exists, which is exactly what user scoping hides.
        when(trips.get(any(UUID.class), any())).thenThrow(new TripNotFoundException());

        mockMvc.perform(get("/api/v1/trips/" + TRIP_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void deletingAnswersTwoZeroFourWithNoBodyAndNoVersion() throws Exception {
        mockMvc.perform(delete("/api/v1/trips/" + TRIP_ID))
                .andExpect(status().isNoContent());

        verify(trips).delete(TRIP_ID, null);
    }

    @Test
    void deletingSomebodyElsesTripIsFourZeroFour() throws Exception {
        doThrow(new TripNotFoundException()).when(trips).delete(any(UUID.class), any());

        mockMvc.perform(delete("/api/v1/trips/" + TRIP_ID))
                .andExpect(status().isNotFound());
    }
}
