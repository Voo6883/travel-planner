package com.travelplanner.application.tripchat;

import com.travelplanner.application.chat.ChatTurn;
import com.travelplanner.application.trip.TripBriefView;
import com.travelplanner.domain.ai.Prompt;
import com.travelplanner.domain.ai.PromptMessage;
import com.travelplanner.domain.model.ClarificationQuestion;
import com.travelplanner.domain.model.TripBriefDetails;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the trip's own state to a turn's prompt, so the model edits the brief the server actually
 * holds rather than the one it inferred from the conversation (task 22, UC-C5-09).
 *
 * <p><strong>The context block is authoritative, and says so.</strong> A trip thread is long-lived;
 * the form and the agent both write the brief, so by the time a turn runs the conversation may
 * describe a brief two edits out of date. The block carries {@code trip.status} and
 * {@code brief.expected_version} straight from the {@link TripBriefView} the orchestrator just
 * read, and the instruction tells the model to trust them over anything said earlier — which is
 * what makes the {@code expected_version} it sends back the one the optimistic lock will accept
 * (ADR 008 §1). The outstanding question ids are listed for the same reason: they are the exact
 * ids {@code answer_clarification} will accept, so the model answers questions that are really
 * open rather than ones it remembers asking.
 *
 * <p>Appended as a second {@code SYSTEM} block rather than merged into the base prompt: the base
 * instruction is shared by both surfaces and owned by {@code ChatTurnService}, and
 * {@link Prompt#systemText()} joins the two, so nothing here has to know how the first was worded.
 */
public final class TripChatPrompt {

    private static final String INSTRUCTIONS = """
            Use update_trip_brief to store only fields the traveller has actually stated, and \
            answer_clarification to resolve the outstanding questions by their id. Always send \
            expected_version exactly as given above; if a tool result reports a newer version, use \
            that one next. Never invent destinations, prices, dates, or other travel facts. Only \
            tell the traveller a field was saved once a tool result confirms it — never claim a \
            value the server rejected was stored.""";

    private TripChatPrompt() {
    }

    /** The turn's prompt with a trip-context system block appended. */
    public static Prompt enrich(ChatTurn turn, TripBriefView view) {
        List<PromptMessage> messages = new ArrayList<>(turn.prompt().messages());
        messages.add(PromptMessage.system(contextBlock(view)));
        return Prompt.adHoc(List.copyOf(messages));
    }

    private static String contextBlock(TripBriefView view) {
        StringBuilder block = new StringBuilder(512);
        block.append("Trip context (authoritative — trust this over the conversation):\n");
        block.append("trip.status: ").append(view.status().name()).append('\n');
        block.append("brief.expected_version: ").append(view.brief().version()).append('\n');
        appendBrief(block, view.brief().details());
        block.append("outstanding_question_ids: ").append(outstandingIds(view)).append("\n\n");
        block.append(INSTRUCTIONS);
        return block.toString();
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
