-- V19 — durable chat persistence (PLAN §3.2 "Persistence: planner_session (pre-trip) + conversation
-- + message per trip", §8 "Chat: planner_session, conversation, message"; ADR 007; tasks/20).
--
-- Three tables, one job: a chat turn must survive a dropped connection, a browser reload, and a
-- retry, and must reload in exactly the order it happened. Everything below exists for one of those
-- three properties.
--
-- ============================================================================================
-- WHAT THE `content` COLUMN MAY HOLD — AND WHAT IT MAY NEVER HOLD
-- ============================================================================================
-- USER-VISIBLE CONTENT ONLY.
--
-- tasks/20 Definition of Done: "No hidden chain-of-thought is stored or returned", and its Do-not
-- list: "Do not expose internal reasoning or raw provider events". A provider reasoning trace
-- (Anthropic `thinking` blocks, OpenAI reasoning summaries, redacted reasoning payloads) must NEVER
-- be written to `message.content` or to any other column in this migration.
--
-- The rule is structural rather than procedural: there is no `reasoning`, `thinking`, `scratchpad`,
-- or `raw_provider_event` column anywhere here, so storing one would mean adding a column, an
-- entity field, a mapper mapping and a domain record component — four reviewed files, not one
-- careless assignment. `ai/langchain4j/` normalises provider streams into the ADR 007 `LlmEvent`
-- union, and only `TextDelta` text and `ToolResult` payloads ever reach this table.
--
-- ============================================================================================
-- WHY ORDERING NEEDS A SEQUENCE AND NOT A TIMESTAMP
-- ============================================================================================
-- `created_at` cannot order a conversation. Three independent reasons, any one of which is fatal:
--
--   1. RESOLUTION. `now()` is fixed for the whole transaction in Postgres, so every row a single
--      turn writes — the tool call, the tool result, the assistant reply — shares one value byte
--      for byte. `ORDER BY created_at` over those rows is not "close enough"; it is unordered, and
--      the database is free to return a different permutation on every execution.
--   2. CLOCKS MOVE BACKWARDS. NTP correction and any future second application instance both make
--      a later write carry an earlier reading. A conversation would then reload with the reply
--      above the question that produced it.
--   3. ADR 007 NEEDS A RESUME CURSOR, NOT A CLOCK. "Every frame carries a monotonic `id` (per
--      conversation) so resume is possible" and "Client sends `Last-Event-ID`; server replays
--      persisted frames after that id". `WHERE seq > :lastEventId` is an exact, index-ordered
--      range scan. `WHERE created_at > :instant` would silently drop or repeat every row sharing
--      the boundary timestamp — which, by (1), is usually the whole rest of the turn.
--
-- So ordering is `message.seq`: a per-conversation counter allocated from
-- `conversation.next_message_seq` under a row lock, unique on `(conversation_id, seq)`. The
-- uniqueness is what makes it a guarantee rather than an intention — a second writer that
-- allocated the same number cannot commit. `created_at` stays, because "when did this happen" is a
-- real question, but it never orders anything.
--
-- ============================================================================================
-- WHY THERE IS NO `version` COLUMN HERE
-- ============================================================================================
-- ADR 008 §1 lists the aggregates that get optimistic locking: trip, trip_brief, itinerary_day,
-- itinerary_item, booking. A conversation is deliberately not one of them.
--
-- Optimistic locking is the right tool when a lost update means lost user intent and the correct
-- answer is to refuse the second write. Appending a message is the opposite: two concurrent
-- appends are both wanted, and both must land. `next_message_seq` is therefore advanced under a
-- PESSIMISTIC_WRITE row lock (`SELECT ... FOR UPDATE`), which queues the second writer instead of
-- failing it. A `@Version` column on `conversation` would turn every concurrent append into a
-- 409 the user did nothing to deserve.

-- --------------------------------------------------------------------------------------------
-- planner_session — the pre-trip chat context (PLAN §3.2 "Planner transport ... (no tripId)").
--
-- The planner home is chat-first: the user talks before any trip exists, so the messages of that
-- conversation need an owner that is not a trip. That owner is this row. When the `create_trip`
-- tool fires, the session's conversation gains a `trip_id` (PLAN §3.2 Handoff: "creates trip +
-- links conversation") and the session is closed by stamping `ended_at`; the conversation keeps
-- its `planner_session_id`, so "this trip started from that planner chat" stays answerable.
-- --------------------------------------------------------------------------------------------
CREATE TABLE planner_session (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL,

    -- Set when the session hands off to a trip, or when the user abandons it. NULL means "this is
    -- the planner chat to resume". Closing by stamping rather than deleting keeps the pre-trip
    -- conversation reachable after the handoff, which is what makes the trip's history complete.
    ended_at    timestamptz,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_planner_session_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE
);

-- At most one OPEN planner session per user. Partial, because closed sessions accumulate and are
-- all equally valid history. Without this, two browser tabs opening the planner home at the same
-- moment would each start a session and the user's chat would split in half.
CREATE UNIQUE INDEX uq_planner_session_user_open ON planner_session (user_id) WHERE ended_at IS NULL;

-- Every read is scoped by user_id (PLAN §4.0.2-L); the list is newest first.
CREATE INDEX ix_planner_session_user_created ON planner_session (user_id, created_at DESC);

-- --------------------------------------------------------------------------------------------
-- conversation — one thread of messages, owned by a user, optionally attached to a trip.
-- --------------------------------------------------------------------------------------------
CREATE TABLE conversation (
    id                  uuid        PRIMARY KEY,

    -- The owner. Present on the conversation rather than only on the trip because a planner
    -- conversation has no trip, and PLAN §4.0.2-L requires every user-owned read to filter on a
    -- user_id it can reach without a join.
    user_id             uuid        NOT NULL,

    -- NULL for a planner conversation. Set by the `create_trip` handoff, after which the thread is
    -- the trip's one persistent conversation (PLAN §3.2 "one persistent conversation from
    -- create_trip until archived").
    trip_id             uuid,

    -- Where the thread started. Kept after the handoff as provenance, not as current state.
    planner_session_id  uuid,

    scope               varchar(16) NOT NULL,
    state               varchar(16) NOT NULL,

    -- The sequence allocator. The NEXT number to hand out, so a brand-new conversation starts at 1
    -- and `seq` is never 0. Advanced under a row lock — see the header note on why this is
    -- pessimistic rather than optimistic.
    next_message_seq    bigint      NOT NULL DEFAULT 1,

    -- Denormalised for the conversation list's "most recent first" ordering, which would otherwise
    -- need an aggregate over `message` per row. NULL until the first message lands.
    last_message_at     timestamptz,

    -- Archived conversations are read-only: history loads, nothing appends (tasks/20 "archived/
    -- read-only behavior"). Same rule as an archived trip, which is view-only for every actor.
    archived_at         timestamptz,

    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES "user" (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_trip FOREIGN KEY (trip_id) REFERENCES trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_planner_session
        FOREIGN KEY (planner_session_id) REFERENCES planner_session (id) ON DELETE SET NULL,

    -- A trip has at most one conversation (PLAN §3.2). Postgres permits unlimited NULLs in a
    -- unique index, so this constrains trip threads without limiting planner threads at all —
    -- which is exactly the asymmetry the product wants.
    CONSTRAINT uq_conversation_trip_id UNIQUE (trip_id),

    -- Mirrors domain/enums/ConversationScope. MigrationContractTest asserts the two lists are
    -- identical, so adding a scope without widening this constraint fails the build rather than
    -- the insert. Same precedent as ck_trip_status (V5).
    CONSTRAINT ck_conversation_scope CHECK (scope IN ('PLANNER', 'TRIP')),
    -- Mirrors domain/enums/ConversationState, asserted by the same test.
    CONSTRAINT ck_conversation_state CHECK (state IN ('ACTIVE', 'ARCHIVED')),

    -- Scope and linkage are one fact, not two. Written as an equivalence so BOTH mistakes are
    -- caught: a TRIP conversation with no trip, and a PLANNER conversation that quietly acquired
    -- one. The handoff flips scope and trip_id in the same UPDATE, so the check never blocks it.
    CONSTRAINT ck_conversation_scope_matches_trip
        CHECK ((scope = 'TRIP') = (trip_id IS NOT NULL)),
    -- Likewise for the archived state and its timestamp: a read-only thread with no record of when
    -- it became read-only is an unanswerable support ticket.
    CONSTRAINT ck_conversation_archived_paired
        CHECK ((state = 'ARCHIVED') = (archived_at IS NOT NULL)),
    CONSTRAINT ck_conversation_next_message_seq_positive CHECK (next_message_seq >= 1)
);

-- The conversation list, scoped by owner (PLAN §4.0.2-L) and newest first — the same shape as
-- ix_trip_user_id_created_at, for the same reason: ordering lives in the index, not in a sort.
CREATE INDEX ix_conversation_user_created ON conversation (user_id, created_at DESC);

-- "Resume my planner chat." Partial, matching the query's own predicate, so the index stays the
-- size of the open sessions rather than of all history.
CREATE INDEX ix_conversation_planner_session ON conversation (planner_session_id)
    WHERE planner_session_id IS NOT NULL;

-- --------------------------------------------------------------------------------------------
-- message — one durable turn element. Append-mostly: a row is inserted when a turn element starts
-- and updated only while an assistant message is still streaming (ADR 007: "Assistant messages are
-- persisted incrementally so resume/reload is served from the DB, not from memory").
-- --------------------------------------------------------------------------------------------
CREATE TABLE message (
    id                 uuid         PRIMARY KEY,
    conversation_id    uuid         NOT NULL,

    -- THE ordering column. Allocated from conversation.next_message_seq under a row lock; see the
    -- header note for why a clock reading cannot do this job.
    seq                bigint       NOT NULL,

    role               varchar(24)  NOT NULL,
    status             varchar(16)  NOT NULL,

    -- USER-VISIBLE CONTENT ONLY. Never a provider reasoning trace, never a raw provider event —
    -- see the header. Starts empty for a streaming assistant message and grows as TextDelta frames
    -- are appended, which is what makes a reload mid-stream show what the user already saw.
    -- Markdown is sanitised server-side on persist (ADR 007 Consequences).
    content            text         NOT NULL DEFAULT '',

    -- IDEMPOTENCY (tasks/20 DoD: "Disconnect/retry cannot duplicate committed user messages").
    --
    -- The client mints this id BEFORE sending and reuses it verbatim on every retry of the same
    -- send. The unique index below is what makes the guarantee hold: the service looks the id up
    -- first and returns the existing message when it is found, but look-then-insert is not atomic,
    -- and two retries racing after a dropped response would both pass the lookup. The constraint
    -- refuses the second INSERT, so the duplicate is impossible rather than unlikely.
    --
    -- NULL for every message the server authors — only a client can retry a send, so only a user
    -- message carries one (ck_message_client_id_is_user_only).
    client_message_id  varchar(64),

    -- ADR 007's `toolCallId`: what joins a TOOL_CALL to the TOOL_RESULT that answered it. Opaque
    -- provider-issued text, not a uuid, because the provider chooses the format.
    tool_call_id       varchar(64),
    tool_name          varchar(64),

    created_at         timestamptz  NOT NULL DEFAULT now(),
    -- Not decorative: a streaming assistant row is UPDATEd as deltas arrive, so this is the only
    -- record of when the last token actually landed.
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    -- When the row stopped being able to change. NULL exactly while status = 'STREAMING'.
    completed_at       timestamptz,

    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id) ON DELETE CASCADE,

    -- Deterministic, stable ordering — and the reason a duplicate sequence allocation cannot
    -- commit. This index also SERVES the ordered history read and the ADR 007 resume scan
    -- (`WHERE conversation_id = ? AND seq > ? ORDER BY seq`), so no separate ordering index exists.
    CONSTRAINT uq_message_conversation_seq UNIQUE (conversation_id, seq),

    -- Per conversation, not global: two users may pick the same client id, and a client that
    -- reuses one across threads is sending two different messages. Multiple NULLs are permitted,
    -- so server-authored rows are unconstrained.
    CONSTRAINT uq_message_conversation_client_id UNIQUE (conversation_id, client_message_id),

    -- Mirrors domain/enums/ChatMessageRole; MigrationContractTest asserts the two lists are identical.
    -- Covers the whole tasks/20 vocabulary — user, assistant, system metadata, tool call, tool
    -- result, lifecycle event — with no room for a "reasoning" role to be added casually.
    CONSTRAINT ck_message_role CHECK (role IN (
        'USER',
        'ASSISTANT',
        'SYSTEM',
        'TOOL_CALL',
        'TOOL_RESULT',
        'LIFECYCLE_EVENT'
    )),
    -- Mirrors domain/enums/ChatMessageStatus, asserted by the same test. INTERRUPTED is ADR 007's
    -- "Partial assistant message persisted with status=interrupted; never silently discarded" —
    -- a disconnect or a cancel marks the row, so a reload can render it as cut short instead of
    -- presenting a truncated answer as a complete one.
    CONSTRAINT ck_message_status CHECK (status IN (
        'STREAMING',
        'COMPLETE',
        'INTERRUPTED',
        'FAILED'
    )),

    CONSTRAINT ck_message_seq_positive CHECK (seq >= 1),

    -- A row is either still streaming or finished; there is no third state, and no finished row
    -- without the instant it finished at.
    CONSTRAINT ck_message_completed_at_matches_status
        CHECK ((status = 'STREAMING') = (completed_at IS NULL)),

    -- Only a client-sent message can be retried, so only a user message may carry a retry key.
    -- Allowing one on an assistant row would invite a "resume" path that re-commits generated
    -- text under an id the client never issued.
    CONSTRAINT ck_message_client_id_is_user_only
        CHECK (client_message_id IS NULL OR role = 'USER'),

    -- Tool correlation belongs to exactly the two tool roles. An equivalence rather than an
    -- implication, so an orphan tool_call_id on an assistant row is refused too — that is how a
    -- TOOL_RESULT ends up unmatchable to the call it answered.
    CONSTRAINT ck_message_tool_call_id_paired
        CHECK ((tool_call_id IS NOT NULL) = (role IN ('TOOL_CALL', 'TOOL_RESULT'))),
    -- A tool invocation without the tool's name cannot be rendered or audited.
    CONSTRAINT ck_message_tool_name_present
        CHECK (role <> 'TOOL_CALL' OR tool_name IS NOT NULL)
);

-- Idempotency lookups hit uq_message_conversation_client_id; ordered history and resume hit
-- uq_message_conversation_seq. `message` intentionally carries NO user_id: the owner lives on
-- `conversation`, and copying it here would create a second source of truth that a mis-set
-- conversation_id could put in disagreement with the first. Every history read joins
-- `conversation` and filters `conversation.user_id` (PLAN §4.0.2-L) — one extra indexed lookup on
-- a primary key.

COMMENT ON TABLE message IS
    'tasks/20 — durable chat turns. content is USER-VISIBLE TEXT ONLY: no chain-of-thought, no raw provider events.';
COMMENT ON COLUMN message.seq IS
    'Per-conversation monotonic order. Timestamps cannot order a turn: now() is transaction-fixed.';
COMMENT ON COLUMN message.client_message_id IS
    'Client-minted retry key, unique per conversation. Makes a re-sent user message impossible to double-commit.';
COMMENT ON COLUMN message.status IS
    'STREAMING while in flight; INTERRUPTED marks a partial assistant message after disconnect or cancel (ADR 007).';
