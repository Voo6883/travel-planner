/**
 * The idempotency key for one user message (task 20 DoD: "Disconnect/retry cannot duplicate
 * committed user messages").
 *
 * Generated on the client, before the request leaves, because that is the only place that knows a
 * retry is the *same* message rather than a second one. A server-generated id cannot help: the
 * request that would have carried it is the one that failed.
 *
 * The same value does two jobs — the server uses it to reject a duplicate commit, and the UI uses
 * it to reconcile the optimistic bubble against the echo — which is what keeps the two in step. A
 * retry that minted a fresh id would satisfy neither.
 */
export function newClientMessageId(): string {
  const webCrypto = globalThis.crypto;
  if (typeof webCrypto?.randomUUID === 'function') {
    return webCrypto.randomUUID();
  }
  // Old Safari and some test environments have no randomUUID. Uniqueness only has to hold within
  // one conversation, and time plus randomness clears that comfortably.
  return `cmid-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
