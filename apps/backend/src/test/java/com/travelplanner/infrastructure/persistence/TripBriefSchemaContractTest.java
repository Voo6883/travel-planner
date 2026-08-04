package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.DateFlexibility;
import com.travelplanner.domain.enums.TravelPace;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.valueobject.PartySize;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Static checks on the migrations that finish {@code trip_brief}.
 *
 * <p>Reads the SQL as text so it runs in the unit suite with no Docker and no database, exactly as
 * {@code MigrationContractTest} does — {@code FlywayMigrationIntegrationTest} proves the file
 * applies, this proves it says what the Java says before anybody starts a container.
 *
 * <p>Kept in its own class rather than added to {@code MigrationContractTest} so that the trip
 * slice's schema assertions travel with the trip slice.
 */
class TripBriefSchemaContractTest {

    private static final String MIGRATION = read("V20__complete_trip_brief_columns.sql");
    private static final String SURPRISE_ME = read("V23__add_trip_brief_surprise_me.sql");

    @Test
    void everyFieldTaskEighteenOwnsHasAColumn() {
        // V6 deferred exactly these to task 18; a field the domain has and the schema does not
        // reads back as null forever and is only noticed by a user losing their answer.
        assertThat(MIGRATION)
                .contains("destinations")
                .contains("date_flexibility")
                .contains("departure_city")
                .contains("party_adults")
                .contains("party_children")
                .contains("interests")
                .contains("pace");
    }

    @Test
    void surpriseMeHasAColumnBecauseUcC105IsNotJustAnExtractionResult() {
        assertThat(SURPRISE_ME)
                .contains("ADD COLUMN surprise_me boolean NOT NULL DEFAULT false")
                .contains("UC-C1-05");
    }

    @Test
    void nothingAddedHereIsNotNullBecauseAnIncompleteBriefMustStillBeSavable() {
        // PLAN §4.1.3 answers incompleteness with typed clarification, not with a rejected save.
        // A NOT NULL scalar here would make a brief under construction unsavable, and the array
        // columns default to empty rather than to unknown.
        String columns = between(MIGRATION, "ALTER TABLE trip_brief\n", ";");

        assertThat(columns.lines().filter(line -> line.contains("NOT NULL")).toList())
                .allSatisfy(line -> assertThat(line).contains("DEFAULT '{}'"));
    }

    @Test
    void theEnumeratedColumnsListExactlyTheirDomainConstants() {
        // Same guarantee MigrationContractTest gives trip.status: a rename on either side is a
        // failing test rather than a constraint violation discovered at deploy time.
        assertThat(constantsIn("ADD CONSTRAINT ck_trip_brief_date_flexibility\n"
                + "        CHECK (date_flexibility IS NULL OR date_flexibility IN ("))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(DateFlexibility.values()).map(Enum::name).toList());
        assertThat(constantsIn("ADD CONSTRAINT ck_trip_brief_pace\n"
                + "        CHECK (pace IS NULL OR pace IN ("))
                .containsExactlyInAnyOrderElementsOf(
                        Stream.of(TravelPace.values()).map(Enum::name).toList());
    }

    @Test
    void thePartyPairingAndBoundsMirrorTheValueObject() {
        // Half a PartySize is not a PartySize, and a direct SQL write must not be able to create a
        // party the domain constructor would have rejected.
        assertThat(MIGRATION)
                .contains("ck_trip_brief_party_paired")
                .contains("(party_adults IS NULL) = (party_children IS NULL)")
                .contains("party_adults >= " + PartySize.MIN_ADULTS)
                .contains("party_adults + party_children <= " + PartySize.MAX_TRAVELLERS);
    }

    @Test
    void theDestinationPreferenceListIsBoundedToTheSameLimitTheDomainEnforces() {
        // An unbounded array on a user-writable row is storage amplification the request body
        // should never have allowed.
        assertThat(MIGRATION).contains(
                "array_length(destinations, 1) <= " + TripBriefDetails.MAX_DESTINATIONS);
    }

    @Test
    void thePreferenceColumnsAreArraysRatherThanJoinTables() {
        // Following poi.tags (V15): short ordered lists read only alongside the rest of the row.
        // A child table would add a join to every brief read for a query that does not exist.
        assertThat(MIGRATION).contains("destinations     text[]").contains("interests        text[]");
        assertThat(MIGRATION).doesNotContain("CREATE TABLE");
    }

    private static List<String> constantsIn(String marker) {
        return Stream.of(between(MIGRATION, marker, "))").split(","))
                .map(value -> value.replace("'", "").trim())
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).describedAs("marker '%s' not found", start).isNotNegative();
        int to = source.indexOf(end, from + start.length());
        assertThat(to).describedAs("marker '%s' not found", end).isNotNegative();
        return source.substring(from + start.length(), to);
    }

    /**
     * Read with line endings normalised to LF.
     *
     * <p>{@code .gitattributes} declares {@code * text=auto}, so these {@code .sql} files arrive with
     * CRLF in a Windows working tree and with LF in CI. The markers above are multi-line literals
     * written with {@code \n}, so without this normalisation every marker that spans a line break
     * fails to match on a developer's machine and matches in the pipeline. A suite that is red
     * locally and green on merge is worse than one that is simply wrong: it teaches everyone to stop
     * believing the local run.
     */
    private static String read(String fileName) {
        try {
            return Files.readString(Path.of("src/main/resources/db/migration").resolve(fileName))
                    .replace("\r\n", "\n");
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
