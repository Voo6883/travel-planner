package com.travelplanner.api.dto.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.travelplanner.domain.enums.ChatMessageRole;
import com.travelplanner.domain.enums.ChatMessageStatus;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * STATUS <b>F-31</b>: the wire is lower-case snake, Java stays upper-case, and the conversion has
 * exactly one home.
 *
 * <p>Parameterised over every constant rather than spot-checked, because the failure this guards
 * against is a constant added later that nobody remembers to publish — and the value of a rule with
 * no hand-written table is precisely that such a constant is published correctly by default. The
 * test is what proves the rule keeps holding for constants that do not exist yet.
 */
class ChatWireNamesTest {

    @ParameterizedTest
    @EnumSource(ChatMessageRole.class)
    void everyRoleIsPublishedAsLowerCaseSnake(ChatMessageRole role) {
        String wire = ChatWireNames.of(role);

        assertThat(wire).isEqualTo(role.name().toLowerCase(Locale.ROOT));
        assertThat(wire).matches("[a-z]+(_[a-z]+)*");
    }

    @ParameterizedTest
    @EnumSource(ChatMessageStatus.class)
    void everyStatusIsPublishedAsLowerCaseSnake(ChatMessageStatus status) {
        String wire = ChatWireNames.of(status);

        assertThat(wire).isEqualTo(status.name().toLowerCase(Locale.ROOT));
        assertThat(wire).matches("[a-z]+(_[a-z]+)*");
    }

    @Test
    void theFourStatusesTheClientMustHandleAreNamedExactlyAsAdrSevenSpellsThem() {
        assertThat(Arrays.stream(ChatMessageStatus.values()).map(ChatWireNames::of).toList())
                .containsExactly("streaming", "complete", "interrupted", "failed");
    }

    @Test
    void theSixRolesAreNamedExactlyAsTheMigrationsCheckConstraintSpellsThemLowerCased() {
        assertThat(Arrays.stream(ChatMessageRole.values()).map(ChatWireNames::of).toList())
                .containsExactly("user", "assistant", "system", "tool_call", "tool_result",
                        "lifecycle_event");
    }

    @Test
    void noWireNameIsEverUpperCase() {
        // The whole point of F-31: the frontend's defensive `toLowerCase()` is deletable only if
        // this can never fail.
        assertThat(Arrays.stream(ChatMessageRole.values()).map(ChatWireNames::of))
                .noneMatch(name -> !name.equals(name.toLowerCase(Locale.ROOT)));
        assertThat(Arrays.stream(ChatMessageStatus.values()).map(ChatWireNames::of))
                .noneMatch(name -> !name.equals(name.toLowerCase(Locale.ROOT)));
    }
}
