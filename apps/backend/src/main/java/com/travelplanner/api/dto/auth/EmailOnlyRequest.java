package com.travelplanner.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of both "send me a mail about this address" endpoints —
 * {@code POST /auth/password/forgot} (UC-A07) and {@code POST /auth/verify-email/resend} (UC-A13).
 *
 * <p>One type for both, deliberately. The two endpoints must be indistinguishable in every
 * observable way, and a shared request shape is the cheapest guarantee that a validation rule added
 * to one is added to the other — a stricter check on one endpoint would give it a different set of
 * rejected inputs, which is a difference an attacker can measure.
 *
 * <p>{@code @Email} is a syntax check only. It rejects {@code "not an address"}, which reveals
 * nothing about anyone, and says nothing about whether the address is registered.
 */
public record EmailOnlyRequest(

        @NotBlank
        @Email
        @Size(max = 320)
        String email) {
}
