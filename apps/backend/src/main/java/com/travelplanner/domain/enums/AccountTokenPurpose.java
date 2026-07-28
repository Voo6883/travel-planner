package com.travelplanner.domain.enums;

/**
 * What a single-use account token authorises (§4.0.10, table {@code account_token} from V8).
 *
 * <p>The purpose is part of the lookup, not just a label. A token minted to confirm an address
 * must not be redeemable as a password reset: the two are issued under different circumstances —
 * one is handed out to anybody who types an address into the sign-up form — and treating them
 * interchangeably would turn "I can receive mail at this address" into "I can set this account's
 * password" for an address that was never proven to belong to the account holder.
 *
 * <p>The names are persisted verbatim, and V8's {@code ck_account_token_purpose} constraint lists
 * exactly these two. Adding a constant means a migration.
 */
public enum AccountTokenPurpose {

    /** UC-A08 — confirms that the account holder can receive mail at the address they registered. */
    EMAIL_VERIFICATION,

    /** UC-A07 — authorises replacing the account's password without knowing the current one. */
    PASSWORD_RESET
}
