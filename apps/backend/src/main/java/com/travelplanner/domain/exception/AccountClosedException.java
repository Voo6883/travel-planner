package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * The target of an administrative action is a closed account. Maps to {@code 409 account_closed}
 * (UC-A14, PLAN §4.0.6).
 *
 * <p>{@code deleted_at IS NOT NULL} means the owner deleted the account. Its row survives so every
 * trip keeps an owner, but it has been anonymised: no username, an unroutable address, and — by
 * the {@code ck_user_deleted_has_no_password} constraint V10 adds — no credential at all.
 *
 * <p>So disabling it changes nothing that was not already true, and resetting its password would
 * put a working credential back on a row that must never hold one. The constraint would reject that
 * write anyway; a typed conflict is a far better answer to an administrator than a constraint
 * violation surfacing as {@code internal_error}.
 *
 * <p>{@code 409} rather than {@code 404}: the account genuinely exists and the administrator is
 * entitled to see it in the list. What is wrong is the account's state, which is a fact the caller
 * needs rather than one to be hidden.
 */
public class AccountClosedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "account_closed";

    public AccountClosedException() {
        super(CODE, "This account has been closed.", Map.of());
    }
}
