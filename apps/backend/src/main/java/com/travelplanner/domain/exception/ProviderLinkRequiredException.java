package com.travelplanner.domain.exception;

import com.travelplanner.domain.enums.AuthProvider;
import java.util.Map;

/**
 * An account already holds this address, but the provider identity may not be attached to it
 * automatically. Maps to {@code 409 provider_link_required}.
 *
 * <p><strong>This exception is the pre-hijack defence.</strong> ADR 009 §4 rejects email equality as
 * proof of ownership, and this is what that rejection looks like at runtime:
 *
 * <ol>
 *   <li>an attacker registers locally as {@code victim@gmail.com} — an address they do not own, so
 *       the verification mail goes to the victim and {@code email_verified} stays {@code false};
 *   <li>the victim later signs in with Google, which asserts the same address, verified;
 *   <li>naive email-equality linking attaches the victim's Google identity to the <em>attacker's</em>
 *       row, and the attacker keeps password access to the victim's trips forever.
 * </ol>
 *
 * <p>Auto-linking therefore requires that the <em>existing</em> account is verified as well as the
 * incoming provider address. When it is not, nothing is linked, nothing is created, and this is
 * raised instead. The recovery path is the explicit confirmation ADR 009 §4 names: sign in to the
 * existing account and link from there, where holding a session is the proof a matching string is
 * not.
 *
 * <p>{@code details.provider} is safe to publish here and nowhere else. Reaching this response at
 * all requires a credential the provider has just verified, so the caller already controls that
 * provider account — the response tells them nothing they did not bring with them.
 */
public class ProviderLinkRequiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "provider_link_required";

    public ProviderLinkRequiredException(AuthProvider provider) {
        super(CODE, "Sign in to your existing account to link this provider.",
                Map.of("provider", provider.name()));
    }
}
