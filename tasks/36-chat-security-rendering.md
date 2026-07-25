# Task 36 — Chat Security and Rendering

## Objective

Harden chat rendering, model/tool boundaries, rate limits, and privacy before release.

## Dependencies

- Tasks 20–22, 27, 31, and 35 complete.

## Required reading

- `plans/superpower/PLAN.md` security, logging, and chat rules
- `plans/BACKLOG.md` S8-3
- `docs/UI-UX-DESIGN-SYSTEM.md` content/accessibility rules

## Scope

### Rendering

- Render supported Markdown through a strict parser and DOMPurify or equivalent sanitizer.
- Disable raw HTML by default; safe link protocols/attributes, external-link behavior, code blocks, lists, and source links.
- Prevent stored/reflected XSS from user, provider, KB, and model content.
- Accessible streaming and reduced-motion behavior.

### Prompt/tool security

- Treat user text, retrieved content, web results, and tool results as untrusted data separated from system instructions.
- Validate all tool names, schemas, enum values, IDs, user ownership, and status gates server-side.
- Reject unknown/oversized/deeply nested arguments.
- Detect/limit prompt-injection patterns without falsely treating retrieved instructions as authority.
- Never expose hidden prompts, secrets, stack traces, internal tool credentials, or chain-of-thought.

### Abuse/privacy

- Rate limits/quotas for chat, research, itinerary, and booking-search endpoints.
- Request/body/message/context length limits and conversation summarization/truncation policy.
- PII-safe structured logs and redaction tests.
- Security headers/CSP compatible with Next.js, OAuth, and PWA.

### Tests

- XSS corpus, malicious Markdown/URLs, prompt injection, tool spoofing, cross-user IDs, oversized input, rate-limit response, and log-redaction tests.

## Do not

- Do not rely on the LLM to enforce authorization.
- Do not log full prompts/messages by default in production.
- Do not render raw provider HTML.

## Definition of Done

- Known XSS/tool-injection paths are blocked by tests.
- Authorization and status gates remain authoritative.
- Rate limits and size bounds are documented.
- Logs contain no tested secrets/PII.

## Handoff

Publish threat model, sanitizer policy, rate limits, context policy, log-redaction rules, and remaining accepted risks.

## Suggested branch and commit

- Branch: `agent/task-36-chat-security`
- Commit: `security: harden chat rendering and tool boundaries`
