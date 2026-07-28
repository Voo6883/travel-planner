package com.travelplanner.api.dto.auth;

import com.travelplanner.domain.valueobject.ProviderCredential;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /auth/providers/{provider}} — the credential to attach to the signed-in account
 * (UC-A09).
 *
 * <p>One opaque field rather than a schema per provider. The {@code {provider}} path segment routes
 * to exactly one adapter and that adapter is the only code which interprets the string, which is
 * the same seam {@code IdentityProviderPort} already uses for sign-in. A per-provider request body
 * would push vendor vocabulary into the contract and into every client generated from it.
 *
 * <p>No user id: the account is the caller's session. A body-supplied id would be a caller
 * asserting whose account gains a new way in.
 */
public record LinkProviderRequest(
        @NotBlank @Size(min = 20, max = 4096) String credential) {

    public ProviderCredential toCredential() {
        return new ProviderCredential(null, credential);
    }
}
