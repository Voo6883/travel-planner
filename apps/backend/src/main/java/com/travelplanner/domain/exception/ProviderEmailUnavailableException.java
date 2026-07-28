package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The provider supplied no address this system may use, so no account can be created from it.
 * Maps to {@code 422 provider_email_unavailable}.
 *
 * <p>The GitHub case ADR 009 §4 legislates: only the <strong>primary and verified</strong> address
 * counts, {@code @users.noreply.github.com} aliases are ignored, and a user whose addresses are all
 * private or unverified leaves nothing to store. That is a refusal rather than a fallback, because
 * every fallback here is worse — a {@code noreply} alias belongs to GitHub rather than to a person,
 * and an unverified address is a claim nobody has checked.
 *
 * <p>Only the <em>creation</em> path raises it. An identity that is already linked signs in
 * perfectly well without a usable address, and an explicit link never consults one: this system
 * joins on {@code (provider, subject)}, and the address is descriptive.
 *
 * <p>{@code 422} rather than {@code 400}: the request was well formed and the caller can do nothing
 * to it. The fix is on GitHub's settings page.
 */
public class ProviderEmailUnavailableException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "provider_email_unavailable";

    public ProviderEmailUnavailableException() {
        super(CODE, "This provider account has no usable email address.", Map.of());
    }
}
