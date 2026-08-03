# ADR 007: Chat streaming transport (POST-SSE) and LLM event model

## Status

Accepted

## Context

`PLAN.md` §3.2 locks chat as the primary product surface, streamed over SSE from
`POST /api/v1/planner/chat/messages` and `POST /api/v1/trips/{tripId}/chat/messages`, and requires
a typed `{ type: "trip_created", trip_id }` event emitted from a **tool call mid-stream**.

Three specification gaps block implementation:

1. **The port cannot carry the protocol.** §5.1 defines `Flux<String> stream(...)`. A token stream
   structurally cannot carry tool-use deltas, stop reasons, usage, or a mid-stream error. `trip_created`
   is unimplementable on top of it. Both Anthropic and OpenAI stream *events*, not strings —
   normalising to `String` discards exactly what the product needs.
2. **The transport contradicts locked frontend rules.** `EventSource` is GET-only and cannot send
   headers, so POST-SSE requires hand-rolled `fetch` + `ReadableStream` — which §4.2 forbids ("no
   raw fetch", "generated client only"). No OpenAPI generator emits a usable `text/event-stream`
   client, so the chat event union — the most safety-critical type in the app — cannot be generated.
3. **No resilience contract.** No heartbeat, no resume, no error frame, no cancellation. Once a
   stream returns `200`, the §6.1 error envelope can no longer be delivered by HTTP status.

## Decision

### Transport

| Item | Choice |
|---|---|
| Method | **POST** + `Accept: text/event-stream` (per §3.2) |
| Client | Hand-authored `lib/api/chat-stream.ts` using `fetch` + `ReadableStream` |
| Codegen | **Documented exception** — this file is the only permitted hand-written API client |
| Heartbeat | `: ping` comment every **15s**; `X-Accel-Buffering: no` to defeat proxy buffering |
| Cancellation | `AbortSignal` client-side; server cancels the agent run on disconnect |
| CSRF | `X-XSRF-TOKEN` header required ([ADR 006](006-cookie-topology-same-origin.md)) |

The codegen exception is deliberate and **narrowly scoped**: request/response *bodies* for all
non-streaming chat endpoints (history, list conversations) remain generated. Only the event union
is hand-authored, and it must be mirrored by a backend sealed type so drift is a compile error on
at least one side.

### Backend event model

`LlmPort` returns `Flux<LlmEvent>`, where `LlmEvent` is a **sealed interface**:

```
TextDelta(text) | ToolUseStart(toolCallId, name) | ToolInputDelta(toolCallId, jsonChunk)
| ToolUseEnd(toolCallId) | ToolResult(toolCallId, payload) | DomainEvent(type, payload)
| Usage(inputTokens, outputTokens, cachedTokens) | Done(stopReason) | StreamError(code, message, details)
```

Provider adapters in `ai/langchain4j/` normalise Anthropic and OpenAI stream events into this
union. `trip_created` is a `DomainEvent`, emitted by the orchestrator **after** the `create_trip`
tool commits — never parsed from assistant prose.

### Wire format

`id:` is emitted on `message_start` / `message_end` only (value = the message's `seq`), not on
every frame. Token deltas are not persisted individually, so a per-delta id would invent a resume
position nothing can serve, and repeating one `seq` across a message's deltas would make a client's
duplicate filter drop every token after the first.

```
id: 42
event: message_start
data: {"message_id":"...","role":"assistant",...}
```

| Concern | Rule |
|---|---|
| Terminal error | `event: error` carrying the §6.1 envelope `{ code, message, details, request_id }`, then close. Never a bare stream abort |
| Resume | Client may send `Last-Event-ID`. **Frame replay is not implemented** — there is no per-delta event store. Reconnect safety is an idempotent re-POST of the same `client_message_id` (unique index): the user message is not duplicated; the prior assistant turn is already `interrupted`. Cost: the answer is regenerated rather than continued |
| Persistence | Assistant messages are persisted incrementally so reload is served from the DB, not from memory |
| Interrupted turn | Partial assistant message persisted with `status=interrupted`; never silently discarded |
| Ordering | Client must handle out-of-order/duplicate frames idempotently after reconnect |

True frame replay remains a future task if a persisted event log is introduced; until then do not
reintroduce per-delta ids. (Amendment recorded as F-39, task 20.)

### OpenAPI representation

The two chat paths are documented in OpenAPI with `text/event-stream` and a **description-only**
body referencing this ADR. The event union lives in the hand-authored client and the backend
sealed type. The CI codegen-drift gate explicitly excludes these two paths.

## Rationale

| Factor | POST-SSE (chosen) | `EventSource` (GET) | WebSocket |
|---|---|---|---|
| Matches locked §3.2 contract | Yes | No — would require re-locking | No |
| Sends message body + CSRF header | Yes | **No** | Yes |
| Works through the ADR 006 proxy | Yes | Yes | Needs upgrade handling |
| Infrastructure complexity | Low | Low | Highest — sticky sessions, ping/pong |
| Built-in reconnect | No — implemented here | Yes | No |
| Half-duplex is sufficient | Yes | Yes | Over-provisioned |

WebSocket is rejected: chat is request→stream, not bidirectional, and it would add connection
management for no product gain. `EventSource` cannot carry a POST body or the CSRF header.

## Consequences

- §5.1's `Flux<String>` is **superseded**. `LlmPort` returns `Flux<LlmEvent>`.
- `Usage` events make token accounting available at the port boundary, which unblocks
  `ai_call_log` (§5.3) — previously unimplementable.
- `lib/api/chat-stream.ts` is exempt from the "no raw fetch" rule (§4.2, §13.2-F6). The exemption
  covers that file only and must be cited in review.
- Markdown is sanitised **server-side on persist** and rendered as plain text while streaming;
  DOMPurify runs on completed messages, not per token frame.
- Proxy/CDN buffering must be verified end-to-end ([ADR 006](006-cookie-topology-same-origin.md)).
- Tasks affected: **14** (AI provider platform — event union), **20** (SSE), **21**, **22**,
  **27**, **31**, **35**, **36**.

## Alternatives considered

- **`Flux<String>` + JSON-in-text-frames** — rejected; pushes protocol parsing into every consumer
  and loses type safety at the port.
- **Polling instead of streaming** — rejected; token-by-token feedback is a core UX property.
- **Generating the SSE client from OpenAPI** — not currently possible; revisit if tooling matures.
- **Discarding interrupted turns** — rejected; users lose long agent responses on transient
  network loss, which is the common mobile case.
