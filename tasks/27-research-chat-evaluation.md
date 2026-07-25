# Task 27 — Research Chat Tools and Evaluation

## Objective

Extend trip chat through C2 and establish the full research prompt/grounding regression gate.

## Dependencies

- Tasks 22, 25, and 26 complete.

## Required reading

- `plans/superpower/PLAN.md` status-gated tool table and C2 decision policy
- `plans/USE-CASES.md` UC-C5-03, C5-04, C5-05 and UC-C2-08/12
- `plans/BACKLOG.md` S4-8, S4-9, S4-10

## Scope

### Chat tools

- `start_research` only when `BRIEF_COMPLETE`, with confirmation behavior from the accepted decision policy.
- Read-only tools such as `get_destination_guide`, `get_travel_apps`, and research status/progress where appropriate.
- `select_recommendation` only after `RESEARCH_READY` and explicit user confirmation, except the documented `just pick for me` case.
- Assistant summaries grounded in persisted recommendation/source data.
- Progress explanations that do not invent percentages or completed facts.
- Query invalidation so chat and structured research screens stay synchronized.

### Notification

- Research-complete mail through `MailerPort`, respecting preference/offline rules and idempotent send behavior.

### Evaluation

- Research cases covering interests, budget, seasonality, unsupported data, contradictory sources, missing provenance, tool loops, prompt injection, malformed output, no-result, provider/tool timeout, and selection policy.
- Metrics for schema validity, source coverage, unsupported claim count, tool-call budget, latency, and estimated cost.
- CI regression on `ai/prompt/` changes using deterministic fixtures; live evaluation remains separately approved.

## Do not

- Do not allow chat to select before results are ready or without allowed confirmation.
- Do not report research complete before persisted terminal state.
- Do not send duplicate completion mail.

## Definition of Done

- Chat can safely initiate, explain, inspect, and select C2 results.
- Structured UI and chat remain consistent.
- Research prompts/tools have a repeatable regression gate.
- Completion notification is idempotent and tested.

## Handoff

Report tool gates, confirmation phrases/policy, evaluation thresholds/results, mail trigger, and selected-destination context for itinerary tasks.

## Suggested branch and commit

- Branch: `agent/task-27-research-chat-evals`
- Commit: `feat: add research chat tools and evaluation`
