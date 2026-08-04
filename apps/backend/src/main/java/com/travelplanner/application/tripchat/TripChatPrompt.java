package com.travelplanner.application.tripchat;

import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.application.trip.TripBriefView;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.enums.TripStatus;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.TripBriefDetails;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends authoritative trip state and tool policy to a trip turn (tasks 22 and 27).
 */
public final class TripChatPrompt {

    private static final String INTAKE_INSTRUCTIONS = """
            Use update_trip_brief to store only fields the traveller has actually stated, and \
            answer_clarification to resolve the outstanding questions by their id. Always send \
            expected_version exactly as given above; if a tool result reports a newer version, use \
            that one next. Never invent destinations, prices, dates, or other travel facts. Only \
            tell the traveller a field was saved once a tool result confirms it — never claim a \
            value the server rejected was stored.""";

    private static final String RESEARCH_INSTRUCTIONS = """
            Research tools are status-gated. Call start_research only when trip.status is \
            BRIEF_COMPLETE and the traveller confirmed (or clearly asked to research now) — set \
            user_confirmed true only then; otherwise ask \"Shall I research options now?\". \
            While research is queued or running, call get_research_status and explain only the \
            persisted status and progress_pct — never invent percentages or claim completion. \
            When RESEARCH_READY, call get_recommendations_summary before summarising options; \
            ground every claim in returned fields and source_refs. Call select_recommendation \
            only after explicit confirmation (confirmation=explicit + recommendation_id), or \
            confirmation=just_pick when the traveller said \"just pick for me\". Use \
            get_destination_guide / get_travel_apps for place questions from the knowledge base. \
            Never invent travel facts.""";

    private TripChatPrompt() {
    }

    /** The turn's prompt with a trip-context system block appended. */
    public static Prompt enrich(ChatTurn turn, TripBriefView view) {
        List<PromptMessage> messages = new ArrayList<>(turn.prompt().messages());
        messages.add(PromptMessage.system(contextBlock(view)));
        return Prompt.adHoc(List.copyOf(messages));
    }

    private static String contextBlock(TripBriefView view) {
        StringBuilder block = new StringBuilder(768);
        block.append("Trip context (authoritative — trust this over the conversation):\n");
        block.append("trip.status: ").append(view.status().name()).append('\n');
        block.append("brief.expected_version: ").append(view.brief().version()).append('\n');
        appendBrief(block, view.brief().details());
        block.append("outstanding_question_ids: ").append(outstandingIds(view)).append("\n\n");
        block.append(instructionsFor(view.status()));
        return block.toString();
    }

    private static String instructionsFor(TripStatus status) {
        return switch (status) {
            case DRAFT, CLARIFICATION_NEEDED -> INTAKE_INSTRUCTIONS;
            default -> RESEARCH_INSTRUCTIONS;
        };
    }

    private static void appendBrief(StringBuilder block, TripBriefDetails details) {
        block.append("destinations: ").append(details.destinations()).append('\n');
        block.append("surprise_me: ").append(details.surpriseMe()).append('\n');
        block.append("dates: ").append(orNotSet(details.dates())).append('\n');
        block.append("date_flexibility: ").append(orNotSet(details.dateFlexibility())).append('\n');
        block.append("departure_city: ").append(orNotSet(details.departureCity())).append('\n');
        block.append("budget: ").append(orNotSet(details.budget())).append('\n');
        block.append("party: ").append(orNotSet(details.party())).append('\n');
        block.append("interests: ").append(details.interests()).append('\n');
        block.append("pace: ").append(orNotSet(details.pace())).append('\n');
    }

    private static String orNotSet(Object value) {
        return value == null ? "not set" : value.toString();
    }

    private static List<String> outstandingIds(TripBriefView view) {
        return view.clarification().questions().stream()
                .map(ClarificationQuestion::id)
                .toList();
    }
}
