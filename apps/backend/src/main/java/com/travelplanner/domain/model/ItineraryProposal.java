package com.travelplanner.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the agent is allowed to decide (task 30, UC-C3-01).
 *
 * <p><strong>Read the shape, not the prose: there are no times here.</strong> The agent chooses
 * <em>which</em> places and <em>in what order</em>, and writes the words around them. It does not
 * say when anything happens, how long a leg takes, or whether a day is feasible — those come from
 * task 28's scheduler and task 29's route policy, which are the authoritative validators the brief
 * requires. A proposal carrying a {@code scheduled_start} would be a model asserting arithmetic
 * nobody can check.
 *
 * <p>Every {@code poiId} must resolve to a real row before this is acted on. That is
 * {@code ItineraryOutputGuardrails}' job, not this record's — a record cannot know which ids the
 * knowledge base holds, and pretending otherwise would put a lookup inside a constructor.
 *
 * @param days one entry per day of the trip, in order. The scheduler decides whether they fit
 * @param narrative the agent's own words about the plan as a whole. Never a source of fact
 */
public record ItineraryProposal(
        List<ProposedDay> days,
        String narrative,
        String promptTemplateId,
        int promptVersion,
        String modelName) {

    public ItineraryProposal {
        days = List.copyOf(Objects.requireNonNull(days, "days"));
        Objects.requireNonNull(promptTemplateId, "promptTemplateId");
        Objects.requireNonNull(modelName, "modelName");

        if (promptTemplateId.isBlank()) {
            throw new IllegalArgumentException("promptTemplateId must not be blank");
        }
        if (promptVersion < 1) {
            throw new IllegalArgumentException(
                    "promptVersion must be at least 1, got " + promptVersion);
        }
        for (int i = 0; i < days.size(); i++) {
            if (days.get(i).dayNumber() != i + 1) {
                throw new IllegalArgumentException("proposed days must run 1.." + days.size()
                        + " in order, found " + days.get(i).dayNumber() + " at position " + (i + 1));
            }
        }
    }

    public boolean isEmpty() {
        return days.isEmpty();
    }

    /** Every POI the proposal names, for the guardrail that checks they all exist. */
    public List<UUID> referencedPoiIds() {
        return days.stream().flatMap(day -> day.stops().stream()).map(ProposedStop::poiId).toList();
    }

    /**
     * One proposed day.
     *
     * @param areaId UC-C3-07's clustering, chosen by the agent from the destination's curated areas.
     *        Absent means "no single area", never "unknown"
     */
    public record ProposedDay(int dayNumber, UUID areaId, List<ProposedStop> stops, String theme) {

        public ProposedDay {
            stops = List.copyOf(Objects.requireNonNull(stops, "stops"));
            if (dayNumber < 1) {
                throw new IllegalArgumentException("dayNumber must be at least 1, got " + dayNumber);
            }
        }

        public Optional<UUID> areaIdIfChosen() {
            return Optional.ofNullable(areaId);
        }

        public Optional<String> themeIfPresent() {
            return Optional.ofNullable(theme);
        }
    }

    /**
     * One proposed stop: a place, and why the agent picked it.
     *
     * <p>{@code poiId} is the whole grounding contract. A stop naming a POI the knowledge base does
     * not hold is a fabricated place, and the guardrails reject the entire proposal rather than
     * dropping the stop — a plan silently missing the temple the model promised in its narrative is
     * worse than a plan that failed loudly.
     */
    public record ProposedStop(UUID poiId, String note) {

        public ProposedStop {
            Objects.requireNonNull(poiId, "poiId");
        }

        public Optional<String> noteIfPresent() {
            return Optional.ofNullable(note);
        }
    }
}
