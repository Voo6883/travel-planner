# Task 25 — Travel Research Agent

## Objective

Implement the bounded, knowledge-grounded C2 agent that retrieves facts and produces validated recommendation candidates/rationale for deterministic ranking.

## Dependencies

- Tasks 14, 17, 23, and 24 complete.

## Required reading

- `plans/superpower/PLAN.md` §4.1 and agent/tool sections
- `plans/USE-CASES.md` C2 and UC-K requirements
- `plans/BACKLOG.md` S4-1, S4-3, S4-4c
- `docs/AI-AGENT-WORKFLOW.md` AI addendum

## Scope

- `TravelResearchAgent` invoked by the research-job worker.
- Status/brief snapshot revalidation before work begins.
- Bounded tool registry for guide, area, food, POI, seasonality, price, transport, route, travel apps, currency, and stub/live supplement ports as available.
- Core knowledge from `KnowledgePort`; optional web search only supplements events/advisories/fresh facts and must retain source URLs.
- Maximum tool calls, token budget, wall-clock timeout, and per-tool input validation.
- Structured candidate output with source references per factual field, uncertainty, risks, cost inputs, and guide sections.
- Guardrails rejecting invented/unsupported destinations, POIs, ratings, prices, apps, or source references.
- Feed validated candidates to Task 24 ranking; LLM writes rationale/narrative but does not overwrite numeric fit score.
- Persist recommendation run, score version/breakdown, provenance, and prompt/model metadata safely.
- Deterministic stub agent/tools for CI; golden-file fixtures and malformed/low-confidence/provider-failure tests.

## Do not

- Do not select a destination for the user.
- Do not generate itinerary or booking actions.
- Do not treat web/LLM output as trusted before validation.
- Do not call external tools inside DB transactions.

## Definition of Done

- Research jobs complete into structured ranked recommendations or a typed no-confident-result.
- Every factual claim is source-linked.
- Tool/model budgets are enforced.
- Provider/tool failures fail closed and leave jobs recoverable.

## Handoff

Document agent prompt/version, tool schemas, budgets, recommendation persistence, source validation, and outputs consumed by Task 26.

## Suggested branch and commit

- Branch: `agent/task-25-research-agent`
- Commit: `feat: implement grounded travel research agent`
