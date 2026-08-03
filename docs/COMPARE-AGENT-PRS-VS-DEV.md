# Comparison — Cursor agent PRs (#13–#18) vs current `dev`

> Written 2026-08-03 after `origin/dev` fast-forwarded from `31c9a69` → `32c8997`
> (15 commits that did **not** come from PRs #13–#18).
>
> Purpose: decide what to keep, rebase, renumber, or close. This is analysis only — no code
> is merged by this document.

**Baselines**

| Side | Tip | Notes |
|---|---|---|
| Current `dev` | `32c8997` | Includes gates 17B, 20C, IT/mail fixes, tooling, AI domain refactor |
| Agent stack tip | `cursor/task-21-planner-chat-15b0` @ `978822a` | Local merge of #13–#17 remainders + task 21 |
| Divergence fork | `31c9a69` | Last common ancestor before the parallel push |

Open agent PRs (still open when this was written): **#13, #14, #15, #16, #17, #18**. #12 was closed as superseded.

---

## 1. Executive verdict

| Agent PR | Topic | vs current `dev` | Recommendation |
|---|---|---|---|
| **#17** | IT unblock (Flyway denylist + stub auto-verify) + Dependency-review soft-fail | **Superseded in spirit.** `dev` already fixed Flyway denylist and mail auto-verify (property name differs). Soft-fail for Dependency review may still be useful if security jobs fail on Dependency graph. | **Close** after confirming CI security job status on `dev`. Cherry-pick only the Dependency-review `continue-on-error` if still needed. |
| **#13** | Task 17 hybrid + `V21__hybrid_fulltext_indexes` | **Superseded / conflicts.** `dev` shipped a different hybrid design (`KnowledgeHybridSearch` RRF) and used **V21/V22** for other schema. | **Close.** Do not merge. |
| **#16** | Task 20 F-39 ADR amend + F-40/F-41 | **Partially superseded.** `dev` closed F-39 with real message-level **replay** (`ChatReplay`) and a stronger ADR amendment. F-40/F-41 appear already addressed on `dev`. | **Close.** Do not merge. Prefer `dev`'s replay implementation. |
| **#14** | Task 18 frontend brief editor + locales | **Still unique and needed.** Gate **18B** is `not_started` on `dev`; `features/intake` is absent. | **Rebase onto `32c8997` and merge.** Highest-value remaining agent delivery after conflict cleanup. |
| **#15** | F-42 `surprise_me` persistence (`V22__add_trip_brief_surprise_me`) | **Still needed** (gate **18B/19B**), but **migration number clashes** with `dev`'s V22. | **Rebase onto `dev` (+ #14), renumber to next free (`V23`)**, then merge. |
| **#18** | Task 21 planner chat + `create_trip` handoff | **Still unique and needed.** Task 21 is `not_started` on `dev`. Branch currently **embeds** superseded #13/#16/#17 history. | **Rebase onto `dev` after #14/#15**, drop superseded commits, keep only planner + intake/surprise commits (or rebuild from `dev` + cherry-picks). |

**Do not merge #13, #16, or #17 as-is onto `dev`.** They will fight migrations and duplicate already-landed behavior with inferior or divergent designs.

---

## 2. What landed on `dev` (15 commits since `31c9a69`)

| Commit | What it did | Overlaps agent work? |
|---|---|---|
| `8c04419` | Close review critical/high findings | Partial (general health) |
| `812a3e5` | Agent-efficiency tooling; doc honesty | No |
| `ee2b6ea` | Travel-app replacement model | Related to knowledge gap F-33; took **V21** |
| `c07dc7e` | Invert shared-chrome → auth dependency | No (frontend architecture) |
| `f6689e9` | Take Reactor out of domain (`LlmStreamPort`) | **Conflicts with any agent branch** that still streams via domain `LlmPort.Flux` assumptions |
| `391fa56` | Vertical-slice generators | No |
| `a563b7a` | Provider replay for deterministic AI tests | No |
| `235f685` | Run container suite without Docker; fix IT defects | **Yes — same problem space as #17** |
| `dcfe03d` / `ab89f76` | HNSW / adapter round-trip ITs (F-32) | Related to task 17 remainder |
| `14bc773` | Seed validation via domain (`SampleSeedDomainCheck`, F-44) | Related to task 17 seed CI |
| `93c7b5a` | STATUS cleanup for task 17 | Docs |
| `37953d7` | Hybrid vector + tsvector RRF (**gate 17B**) + V22 POI fulltext | **Direct overlap with #13** |
| `8f2890a` | Honour `Last-Event-ID` for finished turns (**gate 20C / F-39**) | **Direct overlap with #16**, stronger outcome |
| `32c8997` | Comment-rule docs | No |

### Migrations on `dev` (post-V20)

| Version | File on `dev` | Agent branch used same number for |
|---|---|---|
| **V21** | `V21__create_travel_app_replacement.sql` | `V21__hybrid_fulltext_indexes.sql` (**clash**) |
| **V22** | `V22__poi_fulltext_english_config.sql` | `V22__add_trip_brief_surprise_me.sql` (**clash**) |
| **Next free on `dev`** | **`V23`** | — |

### Ledger gates on `dev` (authoritative slice)

| Gate | Status on `dev` |
|---|---|
| 17A seed loader | `done` |
| 17B hybrid + contracts + seed validate | `done` |
| 17C real curation | `not_started` |
| 18A trip/brief backend | `done` |
| **18B frontend + surprise_me** | **`not_started`** |
| 19A extraction | `done` |
| **19B surprise_me wiring** | **`not_started`** |
| 20A / 20B chat persist + SSE | `done` |
| 20C Last-Event-ID | `done` (replay + ADR amend) |
| **21 planner chat** | **`not_started`** |

Note: `tasks/STATUS.md` still contains some **stale open F-39/F-40/F-41/F-42 rows** alongside later ✅ CLOSED rows for the same IDs. Trust the gate table and the ✅ CLOSED entries dated 2026-07-30; clean the duplicate open rows in a docs PR.

Also: `dev` reused **F-42** for the mail auto-verify IT bug (now closed). Agent PRs used **F-42** for `surprise_me`. The surprise_me work remains open under gates **18B/19B** even if the F-number is overloaded in the ledger.

---

## 3. Topic-by-topic comparison

### 3.1 Knowledge hybrid retrieval (task 17 / gate 17B)

| | `dev` (`37953d7` …) | Agent #13 |
|---|---|---|
| Fusion | `KnowledgeHybridSearch` — reciprocal-rank fusion of vector + fulltext | `HybridRetrievalScore` / earlier `KnowledgeVectorSearch` fusion |
| Fulltext | `KnowledgeFullTextSearch` + **V22** English config | GIN via **V21** `knowledge_tags_text` IMMUTABLE helper |
| Seed CI | `SampleSeedDomainCheck` + `npm run seed:validate` | `validateKnowledgeSeed` / SampleKnowledgeValidator |
| Contracts | `KnowledgePersistenceIT` + vector contract tests | `KnowledgePortContractTest` + adapter contract tests |
| Outcome | **Keep `dev`.** | Close #13; do not port V21 hybrid migration |

### 3.2 Chat resume / F-39 (task 20 / gate 20C)

| | `dev` (`8f2890a`) | Agent #16 |
|---|---|---|
| ADR 007 | Amended: `id:` on start/end; resume table with four cases | Amended: accept header, **no** frame replay |
| Behavior | `ChatTurnService.replay` — if turn **COMPLETE**, replay persisted messages, `done`/`stop_reason: replay`, **no provider call** | Idempotent re-POST only; always regenerate |
| Tests | `ChatReplayTest` (14) + frontend cursor tests | Frontend role widen + history sort removal |
| Outcome | **Keep `dev`** (strictly better product behavior). | Close #16 |

F-40 (six `ChatRole`s) and F-41 (`created_at` re-sort) are already closed/addressed on `dev` independently of #16.

### 3.3 Integration-test / mail auto-verify (#17 vs `235f685`)

| | `dev` | Agent #17 |
|---|---|---|
| Property | `travelplanner.mail.auto-verify-registrations` (defaults from stub, IT sets `false`) | `travelplanner.mail.auto-verify-on-stub` (default `true`, IT sets `false`) |
| Flyway denylist | Already allows chat/knowledge tables; forbids future booking/itinerary/… | Same intent |
| Dependency review | Unchanged (may still fail if Dependency graph disabled) | `continue-on-error: true` on Dependency review step |
| Outcome | Mail/Flyway fixes **already on `dev`**. | Close #17; optionally cherry-pick security soft-fail alone |

### 3.4 TripBrief frontend (task 18B) — agent #14

| | `dev` | Agent #14 |
|---|---|---|
| `features/intake/*` | **Absent** | Present: list, editor, clarification, conflict notice, autosave |
| Locales `trip_brief.json` en/ms | **Absent** | Present (incl. clarify_* keys) |
| `dayjs` direct dep | — | Declared |
| Outcome | **Rebase #14 onto `dev` and land it.** |

### 3.5 `surprise_me` persistence (18B/19B) — agent #15

| | `dev` | Agent #15 |
|---|---|---|
| Column / domain field | **Missing** | `TripBriefDetails.surpriseMe`, OpenAPI, entity, **V22** migration |
| Migration | V22 already used | Must become **V23** (or next free after any intervening merge) |
| Outcome | **Keep the feature; renumber migration; rebase after #14.** |

### 3.6 Planner chat + create_trip (task 21) — agent #18

| | `dev` | Agent #18 |
|---|---|---|
| `application/planner/*` | **Absent** | `PlannerTools`, `CreateTripArgs`, `TripNamer`, `CreateTripHandoffService`, `PlannerChatOrchestrator` |
| Frontend | No planner home chat composition | `PlannerHomePanel`, suggested prompts, navigate on `trip_created`, trip chat panel |
| Handoff doc | — | `docs/PLANNER-CHAT-HANDOFF.md` |
| Branch hygiene | — | **Dirty:** includes full superseded stack (#13/#16/#17 merges) |
| Outcome | **Rebuild/rebase onto clean `dev` + #14/#15.** Do not merge the stacked tip as-is. |

Extra caution: `dev`'s `f6689e9` moved Reactor streaming off the domain `LlmPort`. Task 21 code that assumes `LlmPort.stream(...)` returns `Flux` may need adaptation to `LlmStreamPort` / current AI boundaries before it compiles on `dev`.

---

## 4. Path presence matrix (selected)

| Path | On `dev`? | On agent tip (#18)? |
|---|---|---|
| `application/planner/PlannerChatOrchestrator.java` | no | **yes** |
| `application/chat/ChatReplay.java` | **yes** | no |
| `infrastructure/.../KnowledgeHybridSearch.java` | **yes** | no |
| `infrastructure/.../HybridRetrievalScore.java` | no | yes (superseded design) |
| `features/intake/.../trip-brief-editor.tsx` | no | **yes** |
| `features/chat/.../planner-home-panel.tsx` | no | **yes** |
| `V21__create_travel_app_replacement.sql` | **yes** | no |
| `V21__hybrid_fulltext_indexes.sql` | no | yes (**conflict number**) |
| `V22__poi_fulltext_english_config.sql` | **yes** | no |
| `V22__add_trip_brief_surprise_me.sql` | no | yes (**conflict number**) |

---

## 5. Recommended execution plan

1. **Confirm CI on current `dev`** (backend ITs, security job). If Dependency review still fails the workflow, cherry-pick only the soft-fail hunk from #17.
2. **Close** #12 (already), **#13, #16, #17** with a comment pointing at this document.
3. **Rebase #14** onto `32c8997` → land **18B frontend** (no migration).
4. **Rebase #15** onto that tip → rename migration to **`V23__add_trip_brief_surprise_me.sql`** → land **19B / surprise_me**.
5. **Rebuild #18** from post-#15 `dev`:
   - Cherry-pick or re-apply only planner backend + planner UI commits
   - Fix compile against post-Reactor-domain AI ports
   - Do **not** carry hybrid V21/V22 or ADR-only F-39 commits
6. Mark gates **18B / 19B / 21** in `STATUS.md` in the same PRs that land them.
7. Optionally scrub duplicate stale F-rows in `STATUS.md` / refresh `HANDOFF-REMAINING-WORK.md` (still says next migration `V21`).

### Suggested merge order after cleanup

```
dev@32c8997
  → #14 (18B frontend)
  → #15' (surprise_me as V23)
  → #18' (task 21, rebased)
```

---

## 6. What not to do

- Do **not** merge #18’s current tip into `dev` — Flyway will reject duplicate/conflicting V21/V22 checksums/names.
- Do **not** re-introduce agent hybrid V21 over `travel_app_replacement`.
- Do **not** replace `dev`’s `ChatReplay` with the “amend ADR, never replay” approach from #16.
- Do **not** assume F-42 in the ledger uniquely means `surprise_me` anymore — check gate **18B/19B**.

---

## 7. References

| Artifact | Location |
|---|---|
| Current `dev` tip | `32c8997` |
| Agent task-21 tip | `cursor/task-21-planner-chat-15b0` @ `978822a` |
| Agent PRs | https://github.com/Voo6883/travel-planner/pull/13 … /18 |
| Ledger | `tasks/STATUS.md` |
| Prior handoff | `docs/HANDOFF-REMAINING-WORK.md` (partially stale on migration numbers) |
| Task 21 notes (agent) | `docs/PLANNER-CHAT-HANDOFF.md` (on #18 only until rebased) |
