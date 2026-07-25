# Travel Planner — Use Cases

> Product use case catalog with acceptance criteria. Implements PM review P0/P1 items.
> Technical rules: [`superpower/PLAN.md`](superpower/PLAN.md). Delivery: [`BACKLOG.md`](BACKLOG.md).
> **Knowledge catalog:** [`TRAVEL-KNOWLEDGE-CATALOG.md`](TRAVEL-KNOWLEDGE-CATALOG.md).

**Legend:** ✅ In plan (locked) · 🆕 Added this doc · ⏳ Phase · ❌ Deferred post-v1

---

## Personas

| Persona | Description |
|---|---|
| **Planner** | Primary user — plans and books personal trips |
| **Returning user** | Has existing trips; resumes planning |
| **Admin** | Internal — manages user accounts (dev/docker seed) |

---

## Trip status (wizard progress) 🆕

`trip.status` drives stepper UI and allowed actions.

| Status | Meaning | User can |
|---|---|---|
| `DRAFT` | Trip created, brief incomplete | Edit brief (C1) |
| `BRIEF_COMPLETE` | Valid `TripBrief` saved | Start research (C2) |
| `CLARIFICATION_NEEDED` | Brief incomplete — questions returned | Answer clarification (C1) |
| `RESEARCH_QUEUED` | Job submitted | Wait / leave page |
| `RESEARCH_RUNNING` | Agent in progress | Poll status |
| `RESEARCH_READY` | Ranked recommendations available | View & **select destination** (C2) |
| `DESTINATION_SELECTED` | User picked a recommendation | Generate itinerary (C3) |
| `ITINERARY_READY` | Day-by-day plan exists | Refine (C5), book (C4) |
| `BOOKING_IN_PROGRESS` | Quotes held / confirm pending | Complete booking (C4) |
| `BOOKED` | At least one confirmed booking | View confirmations |
| `ARCHIVED` | User archived trip | View only |

---

## A. Authentication & account

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-A01 | Sign up with email, username, password | P0 | 0b | `POST /auth/register` → verify email sent; `email_verified=false` until confirmed |
| UC-A02 | Sign up with Gmail | P0 | 0b | `POST /auth/firebase` → `is_new_user=true` → welcome email |
| UC-A03 | Sign up with GitHub | P0 | 0b | OAuth callback → auto-register → welcome email |
| UC-A04 | Log in with email or username + password | P0 | 0b | `POST /auth/login` → JWT cookie |
| UC-A05 | Log in with Gmail | P0 | 0b | Same as UC-A02 with `is_new_user=false` |
| UC-A06 | Log in with GitHub | P0 | 0b | OAuth → JWT cookie |
| UC-A07 | Forgot password | P0 | 0b | Email with reset link via Resend; token 1h expiry |
| UC-A08 | Verify email (local) | P0 | 0b | Link in email → `email_verified=true`; block login until verified 🆕 |
| UC-A09 | Link Gmail to existing account | P1 | 0b | Same email → `provider_linked=true` |
| UC-A10 | Log out | P0 | 0b | Cookie cleared; redirect to login |
| UC-A11 | View profile / linked providers | P0 | 0b | `GET /auth/me` |
| UC-A12 | Change password (logged in) | P1 | 1 | `PUT /auth/password` — current + new password 🆕 |
| UC-A13 | Resend verification email | P1 | 1 | `POST /auth/verify-email/resend` 🆕 |
| UC-A14 | Delete account | P1 | 1 | `DELETE /auth/me` — soft-delete user + anonymize PII 🆕 |
| UC-A15 | Admin list users | P0 | 0b | `GET /admin/users` — `ROLE_ADMIN` |
| UC-A16 | Admin reset password | P0 | 0b | `PUT /admin/users/{id}/reset-password` + audit |

### UC-A08 acceptance (email verification gate) 🆕

- Local sign-up cannot access planner until `email_verified=true`.
- Gmail/GitHub sign-up: `email_verified=true` immediately (provider verified).
- Resend verification from login error state.

---

## B. Trip lifecycle

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-T01 | Create trip via **LLM** (`create_trip` tool) | P0 | 1 | User types intent on planner home; LLM creates trip when ready — not manual `POST /trips` |
| UC-T01b | Create trip manually (fallback) | P2 | 1 | `POST /trips` → `DRAFT` — optional "blank trip" for power users |
| UC-T02 | List my trips | P0 | 0b | `GET /trips` — user-scoped, paginated |
| UC-T03 | Open trip stepper | P0 | 1 | Overview shows `status`; stepper reflects progress 🆕 |
| UC-T04 | Rename trip | P2 | 1 | `PUT /trips/{id}` — `name` field |
| UC-T05 | Archive trip | P2 | 1 | `PUT /trips/{id}` → `status=ARCHIVED` |
| UC-T06 | Delete trip | P2 | 1 | `DELETE /trips/{id}` — soft delete |
| UC-T07 | Resume after leaving mid-research | P0 | 1 | Poll job or return to trip → `RESEARCH_RUNNING` or `RESEARCH_READY` 🆕 |

---

## C1 — Intake & clarification

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-C1-01 | Describe trip via **chat** (primary) or form | P0 | 1 | Chat → `update_trip_brief` tool; form auto-save mirrors same `TripBrief` |
| UC-C1-02 | AI extract structured `TripBrief` | P0 | 1 | Guardrails validate JSON schema |
| UC-C1-03 | Edit brief | P0 | 1 | `PUT .../brief` |
| UC-C1-04 | **Answer clarification questions** | P0 | 1 | See below 🆕 |
| UC-C1-05 | “Surprise me” (open destination) | P1 | 1 | `destinations=[]` + flag `surprise_me=true` |
| UC-C1-06 | Validate budget/dates | P0 | 1 | Domain rejects invalid `Money` / `DateRange` |

### UC-C1-04 — Clarification flow 🆕

When brief is ambiguous or incomplete, backend returns **typed** `ClarificationNeeded` — never silent guess.

```json
{
  "status": "CLARIFICATION_NEEDED",
  "questions": [
    {
      "id": "budget_max",
      "prompt_key": "trip_brief.clarify_budget",
      "type": "money",
      "required": true
    },
    {
      "id": "date_flexibility",
      "prompt_key": "trip_brief.clarify_dates",
      "type": "choice",
      "options": ["fixed", "flexible_week", "flexible_month"]
    }
  ]
}
```

| Step | Actor | Action |
|---|---|---|
| 1 | User | Describes trip in **chat** or submits brief form |
| 2 | System | Extraction → if gaps → `trip.status=CLARIFICATION_NEEDED` |
| 3 | User | Answers questions inline (`PUT .../brief/clarification`) |
| 4 | System | Re-validates → `BRIEF_COMPLETE` or more questions |

**Unlock rule:** C2 research disabled until `status=BRIEF_COMPLETE`.

---

## C2 — Research & decision

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-C2-01 | Start research | P0 | 1 | `POST .../research/run` → `job_id` (async) 🆕 |
| UC-C2-02 | Poll research progress | P0 | 1 | `GET .../research/jobs/{jobId}` → `queued|running|completed|failed` 🆕 |
| UC-C2-03 | View ranked recommendations | P0 | 1 | `GET .../ranked-recommendations` when `RESEARCH_READY` |
| UC-C2-04 | See rationale, est. cost, sources | P0 | 1 | Each item has `rationale`, `est_cost`, `traveler_guide`, `source_refs[]` |
| UC-C2-10 | **View traveler guide on recommendation** | P0 | 1 | Overview, food, areas, highlights, practical — per §4.1.2 |
| UC-C2-11 | **Compare places by interests** | P0 | 1 | Rank uses seasonality + price + POI/food match to brief |
| UC-C2-12 | **Ask about a place in chat** | P0 | 1 | `get_destination_guide` — *"what's good to eat?"*, *"best area to stay?"* |
| UC-C2-13 | **See best areas within destination** | P0 | 1 | `traveler_guide.areas[]` — stay vs explore vs day-trip |
| UC-C2-16 | **Download local apps checklist** | P0 | 1 | `local_app_pack` on destination card — essential apps by usage (e.g. 滴滴 for China) |
| UC-C2-05 | No confident result | P0 | 1 | Typed empty result — not hallucination |
| UC-C2-06 | **Select destination** | P0 | 1 | See below 🆕 |
| UC-C2-07 | Re-run research | P1 | 1 | New job; previous results kept as history |
| UC-C2-08 | Email when research completes | P1 | 1 | Resend notification if user offline 🆕 |
| UC-C2-09 | Compare recommendations | P2 | 1 | Card grid UI — no new API |

### UC-C2-01/02 — Async research job 🆕

Long-running agent (up to 90s §14) **must not** block HTTP.

```
POST /api/v1/trips/{tripId}/research/run
  → 202 { job_id, status: "queued" }
  → trip.status = RESEARCH_QUEUED

GET /api/v1/trips/{tripId}/research/jobs/{jobId}
  → { status, progress_pct?, error_code? }

GET /api/v1/trips/{tripId}/ranked-recommendations
  → 200 when completed; 409 research_not_ready otherwise
```

Frontend: React Query polling while `RESEARCH_RUNNING`; show progress component.

### UC-C2-10 — Traveler guide (what travellers want to know)

Each ranked recommendation includes a structured **`traveler_guide`**:

| Section | Answers |
|---|---|
| `overview` | What the place is like — culture, vibe, who it's for |
| `why_now` | Why this timing fits (seasonality + price from historical data) |
| `areas` | Best neighborhoods to stay / explore / day-trip within the destination |
| `food` | Must-try dishes, food districts, dietary notes |
| `highlights` | Top sights & activities matched to trip interests |
| `practical` | Getting around, daily budget band, crowds, safety |
| `mobility` | Transport modes + recommended apps (maps, transit card, ride-hail) |

UI: expandable sections on research cards + chat can summarize any section.

### UC-C2-06 — Select destination 🆕

| Step | Action |
|---|---|
| 1 | User views recommendation cards on research page |
| 2 | User clicks **“Plan this trip”** on one card |
| 3 | `POST .../selected-recommendation` `{ recommendation_id }` |
| 4 | `trip.status=DESTINATION_SELECTED`; stores chosen destination on trip |
| 5 | Stepper unlocks **Itinerary** (C3) |

---

## C3 — Itinerary

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-C3-01 | Generate itinerary | P0 | 1 | Requires `DESTINATION_SELECTED` |
| UC-C3-02 | View by day | P0 | 1 | `itinerary_day` + `itinerary_item` |
| UC-C3-03 | POI grounded with source ref | P0 | 1 | Each item has `source_ref` (place id / URL) 🆕 |
| UC-C3-06 | **Food slots in itinerary** | P0 | 1 | Meal items from `poi.category=food`; area-clustered with sights |
| UC-C3-07 | **Area-clustered days** | P0 | 1 | Days grouped by `destination_area` — minimize cross-city transit |
| UC-C3-08 | **Day timeline view** | P0 | 1 | `scheduled_start` / `scheduled_end` per item — vertical timeline UI |
| UC-C3-09 | **Travel route between stops** | P0 | 1 | `itinerary_leg` — mode, duration, instructions between items |
| UC-C3-10 | **Transport mode per leg** | P0 | 1 | `transport_mode` enum: WALK, METRO, TRAIN, BUS, TAXI, RIDE_HAIL, FERRY… |
| UC-C3-11 | **Local app per leg** | P0 | 1 | Leg shows **locale** app (e.g. 滴滴 on ride leg in Shanghai, not Uber) |
| UC-C3-12 | **Ask route in chat** | P0 | 1 | `get_route` — *"how do I get from Gion to Arashiyama?"* |
| UC-C3-04 | Regenerate itinerary | P1 | 1 | `POST .../itinerary/regenerate` |
| UC-C3-05 | Regenerate single day | P2 | 2 | Via C5 chat or dedicated action |

---

## C4 — Booking

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-C4-01 | Search flights/hotels | P0 | 2 | Stub quotes in Phase 2 sprint 6 |
| UC-C4-02 | Compare quotes | P0 | 2 | List with price, terms |
| UC-C4-03 | Confirm booking (explicit) | P0 | 2 | Human button; idempotency key |
| UC-C4-04 | Price re-validation at confirm | P0 | 2 | `quote_expired` error if stale |
| UC-C4-05 | View booking confirmation | P1 | 2 | Status + provider reference |
| UC-C4-06 | Cancel booking | P2 | 3 | Post-v1 unless vendor supports |

---

## C5 — Trip chat (primary LLM interface)

Persistent conversation per trip. User chats to **create** the plan (C1) and
**continuously enhance** it through research, itinerary, and booking prep.

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-C5-00 | **Start from planner chat** | P0 | 1 | `POST /planner/chat/messages`; *"help me create a plan"* → LLM decides |
| UC-C5-01 | **Chat to plan a trip** (intake) | P0 | 1 | `create_trip` + `update_trip_brief`; syncs brief form |
| UC-C5-02 | **Chat clarification** | P0 | 1 | Agent asks in chat; `answer_clarification` tool; typed fallback UI |
| UC-C5-03 | **LLM decides to start research** | P0 | 1 | When `BRIEF_COMPLETE`, agent offers or runs `start_research` per §3.2 policy |
| UC-C5-04 | **Chat during research** | P1 | 1 | Explain progress; answer questions while job runs |
| UC-C5-05 | **LLM recommends destination** | P0 | 1 | Summarize options + rationale; `select_recommendation` after user confirms |
| UC-C5-06 | **Chat to generate itinerary** | P0 | 1 | After selection → `generate_itinerary` |
| UC-C5-07 | **Chat to enhance itinerary** | P0 | 1 | `patch_itinerary` — structured diff, not free-text replace |
| UC-C5-08 | **Chat history per trip** | P0 | 1 | `conversation` + `message` persisted; reload restores thread |
| UC-C5-09 | SSE streaming responses | P0 | 1 | `POST .../chat/messages` streams tokens + tool events |
| UC-C5-10 | **Chat booking suggestions** | P1 | 2 | `search_booking_quotes`; confirm still via C4 UI button |
| UC-C5-11 | Undo last change | P2 | 3 | Deferred |

### UC-C5-00 — Create from natural language

| Step | Actor | Action |
|---|---|---|
| 1 | User | Lands on `/trips` — sees chat composer + suggestion chips |
| 2 | User | Types *"help me create a plan"* or similar |
| 3 | LLM | Asks 1–2 questions (no trip yet) **or** calls `create_trip` if enough info |
| 4 | System | SSE `trip_created` → navigate to `/trips/{id}`; conversation continues |
| 5 | LLM | Decides next action per §3.2 policy (clarify, research, etc.) |

### UC-C5-01 — Chat-first intake

| Step | Actor | Action |
|---|---|---|
| 1 | User | Describes trip in chat (on trip page or after create) |
| 2 | LLM | Calls `update_trip_brief` → brief form updates; may → `CLARIFICATION_NEEDED` |
| 3 | User | Continues chatting or edits form — both stay in sync |

### UC-C5-07 — Continuous enhancement

After `ITINERARY_READY`, user keeps chatting to refine:

```
User: "Day 2 is too packed — move the museum to day 3"
  → patch_itinerary → itinerary view refreshes + assistant summarizes change
```

Same chat thread from trip creation; no separate "refine mode".

---

## D. Notifications (Resend)

| ID | Use case | Priority | Phase | Template |
|---|---|---|---|---|
| UC-N01 | Welcome email | P0 | 0b | `welcome` |
| UC-N02 | Verify email | P0 | 0b | `verify-email` |
| UC-N03 | Password reset | P0 | 0b | `reset-password` |
| UC-N04 | Research complete | P1 | 1 | `research-complete` 🆕 |
| UC-N05 | Booking confirmed | P1 | 2 | `booking-confirmed` |

---

## E. MVP funnel (must work end-to-end)

```
Sign up (email / Gmail / GitHub)
  → Planner home → type "help me create a plan" (or similar)
  → LLM asks questions / creates trip when ready
  → Chat builds brief → clarify if needed (BRIEF_COMPLETE)
  → LLM offers or runs research (async job)
  → Poll / email notification (RESEARCH_READY)
  → LLM recommends; user confirms destination (DESTINATION_SELECTED)
  → LLM generates itinerary (ITINERARY_READY)
  → Keep chatting to enhance plan
```

Phase 2 adds: booking quotes via chat → confirm in C4 UI.

---

## F. Deferred (post-v1)

| Use case | Notes |
|---|---|
| Guest / try-before-sign-up | §3 later |
| Share / export PDF | §3 later |
| Group planning | §3 later |
| Disruption replanning | §3 later |
| Packing lists | §3 later — C6 candidate |

---

## G. Knowledge-based requirements (TKB)

All factual claims in C2/C3/C5 must be **retrieved from the Travel Knowledge Base** (§4.1.0) — not LLM-generated.

| ID | Use case | Priority | Phase | Acceptance criteria |
|---|---|---|---|---|
| UC-K01 | Retrieve destination guide | P0 | 1 | `KnowledgePort.getGuide` → overview, best_for, practical |
| UC-K02 | Semantic POI search | P0 | 1 | `semanticSearch` — e.g. "street food", "temples" → ranked POIs with `source_ref` |
| UC-K03 | Area lookup within destination | P0 | 1 | `getAreas` — stay / explore / day_trip with vibe tags |
| UC-K04 | Seasonality + price from history | P0 | 1 | `getSeasonality` + `getPriceTrend` feed C2 ranking |
| UC-K05 | Provenance on every fact | P0 | 1 | `source_refs[]` on recommendations, itinerary POIs, chat answers |
| UC-K06 | No invented facts | P0 | 1 | Empty / `low_confidence` when KB has no match — never hallucinate |
| UC-K07 | KB seed data (dev) | P0 | 1 | Flyway seed ≥3 destinations: guides, areas, POIs, **routes, apps** |
| UC-K08 | Live web supplements KB | P1 | 1 | WebSearchTool for events/advisories only — core intel from TKB |
| UC-K09 | **Transport modes per destination** | P0 | 1 | `getTransportModes` — metro, bus, taxi, when to use, payment hint |
| UC-K10 | **Route segments A→B** | P0 | 1 | `findRoutes` — duration, mode, cost band, instructions |
| UC-K11 | **Locale app pack** | P0 | 1 | `getRecommendedApps(country)` — local apps per usage; `local_name` (e.g. 滴滴出行) |
| UC-K12 | **Timeline on itinerary** | P0 | 1 | Items have `scheduled_start/end`; legs between items |
| UC-K13 | **Chat: apps for destination** | P0 | 1 | *"Going to China — what apps?"* → essential pack; no Uber in CN |
| UC-K14 | **Suppress global where inactive** | P0 | 1 | `replaces_global[]` — don't suggest Uber when 滴滴 is required |

---

## Traceability

| Use case range | Feature | PLAN section |
|---|---|---|
| UC-A* | Auth & admin | §4.0.5, §4.0.6, §4.0.10 |
| UC-T* | Trip lifecycle | §8, §4.0.8 |
| UC-C1-* | C1 Intake | §3, §4.1 |
| UC-C2-* | C2 Research | §4.1, §14 |
| UC-C3-* | C3 Itinerary | §3, §4.0.3 |
| UC-C4-* | C4 Booking | §7 |
| UC-C5-* | C5 Trip chat | §3.2 |
| UC-K* | Travel Knowledge Base | §4.1.0, §4.1.2, §5.0 |
| UC-N* | Mailer | §4.0.10 |
