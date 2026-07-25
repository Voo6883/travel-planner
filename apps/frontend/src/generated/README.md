# `src/generated/` — do not edit

Everything under this directory is produced by OpenAPI code generation and overwritten on every
`npm run codegen`. Hand edits are lost and, worse, cause frontend/backend contract drift — the
exact failure the generated-client rule exists to prevent (PLAN §4.2.2, §6).

Empty until [Task 06](../../../../tasks/06-openapi-error-platform.md) wires the generator, which
will create `src/generated/api/`.

**Until then:** [`src/lib/api/health-client.ts`](../lib/api/health-client.ts) is a temporary,
explicitly-marked stand-in covering only the health endpoint. It is deleted — not extended — when
generation lands.
