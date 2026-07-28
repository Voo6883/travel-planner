-- V11 — ai_call_log (PLAN §5.3, §8 "Ops: ai_call_log (tokens/cost/provider)"; backlog S2-5).
--
-- MERGE NOTE: migrations V1–V10 existed when this was written. Task 10 (external identity
-- providers) is being implemented in parallel and may also claim V11. If both land, renumber this
-- one — it has no dependency on any other migration in this range.
--
-- ============================================================================================
-- WHAT THIS TABLE DELIBERATELY DOES NOT CONTAIN
-- ============================================================================================
-- No prompt text. No completion text. No user message. No email address.
--
-- AI-AGENT-WORKFLOW A4 requires "tokens + latency, not full prompts in prod", and PLAN §9 forbids
-- PII in logs. A prompt in this system contains the traveller's dates, budget, who they are going
-- with, and whatever they typed into chat. This is an operational table with long retention and a
-- wide read audience, which makes it the worst possible place for that.
--
-- prompt_hash replaces it: a SHA-256 of the rendered prompt. It answers what operations actually
-- asks — "same prompt as the one that failed?", "did the retry send something different?" — and
-- cannot be reversed into someone's itinerary.
--
-- The guarantee is structural rather than procedural: AiCallRecord has no field able to hold prompt
-- text, so logging one would require changing the record, the entity, the mapper, and this file.
--
-- user_id is kept. It is an internal surrogate key, not personal data, and without it per-user cost
-- attribution and abuse investigation are impossible. No FK to user(id): this is an append-only
-- operational log, and a foreign key would either block the account deletion V10 supports or
-- cascade away the cost history that outlives the account.

CREATE TABLE ai_call_log (
    id              uuid          PRIMARY KEY,

    -- The X-Request-Id from the MDC (PLAN §4.0.2-J2). Nullable: background research jobs and the
    -- re-embed pipeline have no HTTP request behind them.
    request_id      varchar(64),
    user_id         uuid,

    -- The routing key from LlmOptions.feature() — 'default', 'research', 'nl-search'. Free-form
    -- rather than a CHECK constraint: every feature task adds one, and a constraint would make each
    -- of those a migration.
    feature         varchar(64)   NOT NULL,
    operation       varchar(32)   NOT NULL,
    provider        varchar(32)   NOT NULL,

    -- May be empty when the caller did not override the model and the adapter's configured default
    -- applied. Empty is honest; guessing a name here would corrupt the cost estimate.
    model           varchar(128)  NOT NULL DEFAULT '',

    input_tokens    integer       NOT NULL DEFAULT 0,
    output_tokens   integer       NOT NULL DEFAULT 0,
    -- Prompt tokens served from a provider-side cache, billed at roughly a tenth of the input rate.
    -- Separate because folding them into input_tokens overstates the cost of a long conversation
    -- severalfold — and long conversations are the product (PLAN §3.2).
    cached_tokens   integer       NOT NULL DEFAULT 0,

    latency_ms      bigint        NOT NULL DEFAULT 0,

    -- numeric, not double precision: this is money (PLAN §6 item 4, §13.1). Per-call amounts are
    -- fractions of a cent, and floating point accumulating millions of them drifts.
    cost_amount     numeric(14,6) NOT NULL DEFAULT 0,
    cost_currency   char(3)       NOT NULL DEFAULT 'USD',

    outcome         varchar(16)   NOT NULL,
    -- Registered in api/openapi/errors.yaml. A code, never a provider message: a provider's error
    -- body can quote the prompt back, which would reintroduce exactly what prompt_hash removes.
    error_code      varchar(64),

    -- SHA-256 hex of the rendered prompt. 64 characters, or empty for an operation with no prompt.
    prompt_hash     varchar(64)   NOT NULL DEFAULT '',

    created_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_ai_call_log_outcome
        CHECK (outcome IN ('OK', 'ERROR', 'CANCELLED')),
    -- An error_code on a successful call, or a success with no code, means the recorder disagreed
    -- with itself. Cheap to assert here; expensive to notice in a dashboard six weeks later.
    CONSTRAINT ck_ai_call_log_error_code
        CHECK ((outcome = 'ERROR') = (error_code IS NOT NULL)),
    CONSTRAINT ck_ai_call_log_tokens
        CHECK (input_tokens >= 0 AND output_tokens >= 0 AND cached_tokens >= 0),
    CONSTRAINT ck_ai_call_log_cost CHECK (cost_amount >= 0)
);

-- "What did this feature cost, and how did it behave, over the last N days" — the query behind every
-- cost and error-rate view.
CREATE INDEX ix_ai_call_log_feature_created ON ai_call_log (feature, created_at DESC);

-- Tracing a user-reported failure from its X-Request-Id to the AI calls it made.
CREATE INDEX ix_ai_call_log_request_id ON ai_call_log (request_id) WHERE request_id IS NOT NULL;

-- Per-user cost attribution and abuse investigation.
CREATE INDEX ix_ai_call_log_user_created ON ai_call_log (user_id, created_at DESC)
    WHERE user_id IS NOT NULL;

COMMENT ON TABLE ai_call_log IS
    'PLAN §5.3 — per-call AI observability. Contains NO prompt text, completion text, or PII.';
COMMENT ON COLUMN ai_call_log.prompt_hash IS
    'SHA-256 of the rendered prompt. Never the prompt itself (AI-AGENT-WORKFLOW A4, PLAN §9).';
COMMENT ON COLUMN ai_call_log.cached_tokens IS
    'Prompt tokens served from a provider-side cache; a subset of input_tokens, billed at a discount.';
