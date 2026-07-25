# Task 19 — LLM TripBrief Extraction

## Objective

Add validated natural-language extraction and clarification to the deterministic C1 foundation.

## Dependencies

- Tasks 14 and 18 complete.

## Required reading

- `plans/superpower/PLAN.md` C1 clarification, structured-output, and AI guardrail rules
- `plans/USE-CASES.md` UC-C1-01, C1-02, C1-04, C1-05
- `plans/BACKLOG.md` S3-3
- `docs/AI-AGENT-WORKFLOW.md` AI addendum

## Scope

- Versioned extraction prompt(s) using provider-neutral structured completion.
- JSON-schema-bound output that maps into the accepted `TripBrief` command, never directly into persistence entities.
- Detect missing/ambiguous critical fields and return typed `ClarificationNeeded` rather than guessing.
- Support `surprise_me` and flexible dates as defined by the plan.
- Validate money/date/party/domain rules after model output.
- Bounded retry for malformed structured output, followed by deterministic form/clarification fallback.
- Token/latency/cost logging through Task 14 infrastructure.
- Prompt-injection-aware separation between user text and system/schema instructions.
- Golden-file and evaluation fixtures introduced now, before C2: complete, incomplete, vague, invalid dates, impossible budget, multilingual inputs if supported, timeout, malformed output, and unsupported destinations.
- Service integration that performs LLM work outside transactions and persists only validated results in a short write transaction.

## Do not

- Do not let extraction start research or create bookings.
- Do not persist raw model prose as TripBrief truth.
- Do not silently fill missing budget/date/departure fields.
- Do not require live models in CI.

## Validation

- Deterministic stub tests.
- Golden-file regression suite.
- Schema-validity and clarification thresholds documented.
- Optional manually approved live-provider evaluation with cost/latency report.

## Definition of Done

- Natural-language input reliably yields a valid brief or typed clarification.
- Domain validation remains authoritative.
- Model/provider failure falls back safely.
- Prompt changes are covered by an evaluation gate.

## Handoff

Document prompt version, output schema, evaluation results, retry/fallback behavior, and service methods for chat tools.

## Suggested branch and commit

- Branch: `agent/task-19-llm-trip-brief`
- Commit: `feat: add validated LLM trip brief extraction`
