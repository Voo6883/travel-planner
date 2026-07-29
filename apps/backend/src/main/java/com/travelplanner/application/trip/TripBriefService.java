package com.travelplanner.application.trip;

import com.travelplanner.application.knowledge.SupportedDestinationService;
import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.exception.DestinationNotCoveredException;
import com.travelplanner.domain.exception.TripNotFoundException;
import com.travelplanner.domain.model.ClarificationNeeded;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.Trip;
import com.travelplanner.domain.model.TripBrief;
import com.travelplanner.domain.model.TripBriefDetails;
import com.travelplanner.domain.model.TripStatusTransition;
import com.travelplanner.domain.model.Versioned;
import com.travelplanner.domain.port.TripBriefRepositoryPort;
import com.travelplanner.domain.port.TripRepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C1 intake — the deterministic half (PLAN §4.1.3, UC-C1-04). No LLM is called from here; task 19
 * adds extraction on top of exactly these entry points.
 *
 * <h2>Completeness is computed, never asserted</h2>
 *
 * <p>Every write recomputes {@link ClarificationNeeded} from the saved brief and moves
 * {@code trip.status} to match. There is no path that sets {@code BRIEF_COMPLETE} without the
 * brief actually being complete, which is what makes PLAN §3.1's "C2 is blocked until
 * {@code BRIEF_COMPLETE}" a property of the data rather than a rule three call sites have to
 * remember.
 *
 * <h2>Two aggregates, two versions</h2>
 *
 * <p>{@code expected_version} on both write paths is the <em>brief's</em> version. The status
 * update that follows a save advances the <em>trip's</em> version as a side effect, so a client
 * holding a trip it read before the save will get {@code 409} on its next rename — correctly. That
 * is ADR 008 §4's "no implicit winner" applied to a change the client did not make itself, and the
 * response body carries the fresh trip status so the re-read is one field, not one request.
 *
 * <h2>Destination preference is checked against coverage</h2>
 *
 * <p>ADR 010 §4. A brief naming a destination the knowledge base has never curated cannot be
 * researched honestly — it would score near zero on three of four fit terms and read to the user as
 * "we considered it and it is a poor match". The refusal is the typed
 * {@link DestinationNotCoveredException}, which carries the covered list so the client can offer an
 * alternative in the same breath. The coverage rule itself is not re-implemented here:
 * {@link SupportedDestinationService} is the one place that answers it.
 */
@Service
@RequiresDatabase
public class TripBriefService {

    private final TripAccess access;
    private final TripRepositoryPort trips;
    private final TripBriefRepositoryPort briefs;
    private final SupportedDestinationService destinations;

    public TripBriefService(TripAccess access, TripRepositoryPort trips,
            TripBriefRepositoryPort briefs, SupportedDestinationService destinations) {
        this.access = access;
        this.trips = trips;
        this.briefs = briefs;
        this.destinations = destinations;
    }

    /**
     * The brief, the trip status, and the outstanding questions — the read that precedes every
     * write, and therefore the read that returns the {@code version} the write must echo
     * (ADR 008 §1).
     */
    @Transactional(readOnly = true)
    public TripBriefView get(UUID tripId, UserContext user) {
        Trip trip = access.requireOwned(tripId, user);
        TripBrief brief = requireBrief(tripId);
        return new TripBriefView(brief, trip.status(),
                ClarificationNeeded.forDetails(brief.details()));
    }

    /**
     * Replaces every editable field of the brief — the whole-body save the debounced form issues.
     *
     * @throws com.travelplanner.domain.exception.VersionConflictException when
     *         {@code expectedVersion} is not the brief's stored version, carrying
     *         {@code details.current_version}
     * @throws DestinationNotCoveredException when a named destination is not fully curated
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the trip is
     *         archived, or a submitted value fails its own invariant
     */
    @TransactionalWrite
    public TripBriefView save(SaveTripBriefCommand command, UserContext user) {
        Trip trip = access.requireEditable(command.tripId(), user);
        TripBrief stored = requireBrief(command.tripId());
        Versioned.requireVersion(stored, command.expectedVersion());

        TripBriefDetails details = command.details() == null
                ? TripBriefDetails.empty()
                : command.details();
        requireCoveredDestinations(details.destinations());
        return persist(trip, stored, details);
    }

    /**
     * Answers one or more outstanding questions and re-validates (UC-C1-04 step 4).
     *
     * <p>Only outstanding questions may be answered, and only with the value type they asked for;
     * {@link ClarificationNeeded#applyAnswers} refuses anything else rather than dropping it. That
     * is what stops this endpoint from becoming a second, unvalidated way to write arbitrary brief
     * fields.
     *
     * <p>No coverage check: no clarification question sets a destination, so there is nothing here
     * that could name an uncovered one.
     */
    @TransactionalWrite
    public TripBriefView answerClarification(AnswerClarificationCommand command, UserContext user) {
        Trip trip = access.requireEditable(command.tripId(), user);
        TripBrief stored = requireBrief(command.tripId());
        Versioned.requireVersion(stored, command.expectedVersion());

        TripBriefDetails outstanding = stored.details();
        TripBriefDetails revised = ClarificationNeeded.forDetails(outstanding)
                .applyAnswers(outstanding, command.answers() == null ? List.of() : command.answers());
        return persist(trip, stored, revised);
    }

    /**
     * Saves the brief, then brings {@code trip.status} in line with what the saved brief now says.
     *
     * <p>The brief is written first so that the status can never claim a completeness the stored
     * brief does not have. If the trip write then fails, the whole transaction rolls back — both
     * rows or neither, in the documented lock order.
     */
    private TripBriefView persist(Trip trip, TripBrief stored, TripBriefDetails details) {
        Instant now = Instant.now();
        TripBrief saved = briefs.save(stored.withDetails(details, now));
        ClarificationNeeded clarification = ClarificationNeeded.forDetails(saved.details());
        TripStatus target = clarification.isSatisfied()
                ? TripStatus.BRIEF_COMPLETE
                : TripStatus.CLARIFICATION_NEEDED;

        TripStatus current = trip.status();
        if (current != target) {
            // Refuses a trip that has already left the intake phase. Task 22 widens the allowed set
            // together with the endpoint that moves a trip into research.
            TripStatusTransition.require(current, target);
            current = trips.save(trip.withStatus(target, now)).status();
        }
        return new TripBriefView(saved, current, clarification);
    }

    /**
     * @throws DestinationNotCoveredException naming the first uncovered slug, with the covered list
     *         attached. One refusal at a time is deliberate: the client's next action is to replace
     *         that slug, and a list of every problem at once is a form nobody reads
     */
    private void requireCoveredDestinations(List<String> requested) {
        if (requested.isEmpty()) {
            return;
        }
        List<String> supported = destinations.listSupported().stream()
                .map(Destination::slug)
                .toList();
        for (String slug : requested) {
            if (!supported.contains(slug)) {
                throw new DestinationNotCoveredException(slug, supported);
            }
        }
    }

    /**
     * @throws TripNotFoundException when the brief row is missing. Unreachable through
     *         {@link TripService#create}, which writes both rows in one transaction — but a
     *         {@code 404} is still the truthful answer for a resource that is not there, and it
     *         keeps a broken invariant from surfacing as a 500 the user cannot act on
     */
    private TripBrief requireBrief(UUID tripId) {
        return briefs.findByTripId(tripId).orElseThrow(TripNotFoundException::new);
    }
}
