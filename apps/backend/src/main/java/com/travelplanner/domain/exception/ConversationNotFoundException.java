package com.travelplanner.domain.exception;

import java.util.Map;

/**
 * No conversation with that id belongs to the caller. Maps to {@code 404 not_found}.
 *
 * <p>Raised for a conversation that does not exist and for one that belongs to somebody else, with
 * no way to tell the two apart — {@link com.travelplanner.domain.port.ConversationRepositoryPort}
 * publishes no unscoped lookup, so the distinction is not available to leak. A chat thread is the
 * most personal thing this application stores; an endpoint that answered {@code 403} for "exists
 * but not yours" would let anyone enumerate other people's conversations one id at a time.
 *
 * <p>Reuses the registered {@code not_found} code rather than adding a {@code conversation_not_found}
 * one, for the reason {@link TripNotFoundException} gives: the client's handling is identical to
 * every other 404, and a second code would need registering, translating twice, and keeping in step
 * forever to say the same thing.
 */
public class ConversationNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    /** Registered in {@code api/openapi/errors.yaml}; unregistered codes are downgraded. */
    public static final String CODE = "not_found";

    public ConversationNotFoundException() {
        super(CODE, "The requested resource does not exist.", Map.of());
    }
}
