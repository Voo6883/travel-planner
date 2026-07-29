package com.travelplanner.application.trip;

import com.travelplanner.application.knowledge.SupportedDestinationService;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.model.Destination;
import com.travelplanner.domain.model.TripBriefExtraction;
import com.travelplanner.domain.model.TripBriefExtractionRequest;
import com.travelplanner.domain.port.TripBriefExtractionPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * C1 intake — the natural-language half (task 19; UC-C1-01, UC-C1-02, UC-C1-05).
 *
 * <p>It adds one thing to {@link TripBriefService}: a sentence goes in where a form body used to.
 * Everything after the model call is the deterministic path task 18 already built — the same
 * {@code save}, the same version check, the same completeness computation, the same status
 * transition. There is no second way to write a brief, which is what keeps a chat-authored brief
 * and a form-authored one identical rows.
 *
 * <h2>The transaction shape, which is the point of this class</h2>
 *
 * <pre>{@code
 * get(...)      // short read transaction — TripBriefService opens and closes it
 * extract(...)  // seconds of network wait, NO transaction, NO pooled connection held
 * save(...)     // short write transaction — TripBriefService opens and closes it
 * }</pre>
 *
 * <p>{@link #extract} carries no {@code @Transactional} annotation of any kind, and it must never
 * acquire one. A model call inside a transaction pins a HikariCP connection for the whole
 * round trip; at any real concurrency the pool is exhausted by requests that are all waiting on a
 * third party, and the database work that could have completed queues behind them
 * (AI-AGENT-WORKFLOW §6 "LLM call inside {@code @Transactional}").
 *
 * <p>The two transactions are separate, so a brief the user edited in between is not silently
 * overwritten: {@code save} echoes the version read at the start, and a concurrent write makes it a
 * {@code version_conflict} — the honest answer ADR 008 §4 asks for, rather than an extraction that
 * quietly won.
 *
 * <h2>Coverage is checked before the write, not by it</h2>
 *
 * <p>{@link TripBriefService#save} refuses a brief naming an uncurated destination
 * ({@code destination_not_covered}, ADR 010 §4). That is right for a form, where the user chose the
 * slug from a list, and wrong here: a model that reads "Osaka" out of a message would cost the
 * traveller every other field they just typed. So the uncovered slugs are removed first and
 * reported on the result, and the rest of the extraction is saved.
 */
@Service
@RequiresDatabase
public class TripBriefExtractionService {

    private final TripAccess access;
    private final TripBriefService briefs;
    private final TripBriefExtractionPort extraction;
    private final SupportedDestinationService destinations;

    public TripBriefExtractionService(TripAccess access, TripBriefService briefs,
            TripBriefExtractionPort extraction, SupportedDestinationService destinations) {
        this.access = access;
        this.briefs = briefs;
        this.extraction = extraction;
        this.destinations = destinations;
    }

    /**
     * Extracts what the traveller wrote into their brief and persists what survives validation.
     *
     * @throws com.travelplanner.domain.exception.TripNotFoundException when the trip is missing or
     *         belongs to someone else
     * @throws com.travelplanner.domain.exception.ValidationFailedException when the trip is
     *         archived, or the message is blank or longer than
     *         {@link TripBriefExtractionRequest#MAX_USER_TEXT_LENGTH}
     * @throws com.travelplanner.domain.exception.VersionConflictException when the brief changed
     *         between the read and the write
     */
    public TripBriefExtractionResult extract(ExtractTripBriefCommand command, UserContext user) {
        // Ownership and the archived rule are checked before a token is spent, and through the same
        // TripAccess the form path uses. The alternative — letting the save at the end refuse it —
        // pays a provider for an extraction that was never going to be written.
        access.requireEditable(command.tripId(), user);
        TripBriefView current = briefs.get(command.tripId(), user);
        TripBriefExtraction extracted = extraction.extract(new TripBriefExtractionRequest(
                command.userText(), command.locale(), current.brief().details()));

        List<String> uncovered = uncovered(extracted.details().destinations());
        TripBriefExtraction persistable = extracted.withoutDestinations(uncovered);
        TripBriefView saved = briefs.save(new SaveTripBriefCommand(command.tripId(),
                current.brief().version(), persistable.details()), user);
        return new TripBriefExtractionResult(saved, persistable, uncovered);
    }

    /**
     * @return the requested slugs the knowledge base does not fully cover, in the order they were
     *     requested. The coverage rule itself is not re-implemented here — it is whatever
     *     {@link SupportedDestinationService} says, the same source {@link TripBriefService} uses
     */
    private List<String> uncovered(List<String> requested) {
        if (requested.isEmpty()) {
            return List.of();
        }
        List<String> supported = destinations.listSupported().stream()
                .map(Destination::slug)
                .toList();
        return requested.stream().filter(slug -> !supported.contains(slug)).toList();
    }
}
