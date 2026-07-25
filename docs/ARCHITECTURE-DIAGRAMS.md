# Travel Planner — Architecture Diagrams

> Visual reference for system architecture, flows, and activity.  
> Source of truth: [`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md), [`plans/USE-CASES.md`](../plans/USE-CASES.md).

---

## Logic & flow review

### Core thesis (consistent across docs)

| Principle | How it is enforced |
|---|---|
| **Chat-first UX** | Planner home and per-trip chat are the primary interface; stepper/forms are synced mirrors of chat-driven state |
| **Knowledge-based AI** | Retrieve from TKB → Reason with LLM → Grounded output with `source_refs[]` — never invent facts |
| **Status-gated progression** | `trip.status` drives allowed tools, UI steps, and API gates |
| **Human-confirmed booking** | LLM proposes quotes; user explicitly confirms in C4 UI — never auto-books |
| **Async long work** | Research agent (≤90s) runs as background job; HTTP returns `202` + `job_id` |

### Trip status state machine (logic gates)

The status machine is the **central coordination mechanism**. Every feature (C1–C5) reads and writes through it:

```
DRAFT ──────────────────────────────────────────────┐
  │                                                  │
  ├─ update_trip_brief (gaps) ─► CLARIFICATION_NEEDED
  │         │                                        │
  │         └─ answer_clarification ─► BRIEF_COMPLETE
  │                                                  │
  └─ valid brief ─────────────────────► BRIEF_COMPLETE
                                              │
                                              ▼
                                    start_research (async)
                                              │
                         ┌────────────────────┼────────────────────┐
                         ▼                    ▼                    ▼
                  RESEARCH_QUEUED    RESEARCH_RUNNING      (poll job)
                         │                    │
                         └────────┬───────────┘
                                  ▼
                           RESEARCH_READY
                                  │
                                  ▼ select_recommendation
                         DESTINATION_SELECTED
                                  │
                                  ▼ generate_itinerary
                          ITINERARY_READY
                                  │
                    ┌─────────────┴─────────────┐
                    ▼                           ▼
            patch_itinerary (C5)      search_booking_quotes (C4)
                    │                           │
                    │                           ▼
                    │                  BOOKING_IN_PROGRESS
                    │                           │
                    │                           ▼
                    │                        BOOKED
                    └───────────────────────────┘
```

**Review verdict:** Logic is sound. Key gates are explicit:
- C2 blocked until `BRIEF_COMPLETE`
- C3 blocked until `DESTINATION_SELECTED`
- C4 blocked until `ITINERARY_READY`
- Booking confirm is UI-only, not an LLM tool

### Chat orchestration (dual orchestrators)

| Orchestrator | Scope | Tools |
|---|---|---|
| `PlannerChatOrchestrator` | Pre-trip (`POST /api/v1/planner/chat/messages`) | `create_trip` only |
| `TripChatOrchestrator` | Per-trip (`POST /api/v1/trips/{id}/chat/messages`) | Status-gated tools per §3.2 |

**LLM decision policy** prevents premature actions:
- Vague opener → ask 1–2 questions, no trip yet
- Partial info → `create_trip` + `update_trip_brief` + ask gaps
- Ambiguous brief → `CLARIFICATION_NEEDED`, never silent guess
- `RESEARCH_READY` → summarize + wait for user confirmation before `select_recommendation`

**Review verdict:** Clean separation. Handoff via SSE `trip_created` event links planner session → trip conversation.

### Knowledge-based AI pattern (C2, C3, C5)

All factual claims flow through `KnowledgePort`:
1. **RETRIEVE** — SQL + pgvector RAG from TKB
2. **REASON** — LLM ranks, compares, narrates (optional WebSearch for events/advisories)
3. **GROUND** — Structured DTOs with `source_refs[]` on every fact

**Review verdict:** Correct layering. Domain depends on port, not pgvector/JPA. Agent tools call application layer, not infrastructure directly.

### Potential edge cases (documented, not gaps)

| Scenario | Handling |
|---|---|
| User leaves during research | Poll job on return; optional research-complete email |
| Research fails | `research_job.status=failed` + `error_code`; **`trip.status` stays at last valid value**; re-run allowed |
| Quote expires at booking | Price re-validation at confirm; `quote_expired` error |
| OAuth same email | Provider linking (`provider_linked=true`) |
| Local sign-up unverified | Blocked from planner until `email_verified=true` |

---

## 1. System architecture

### 1.1 Container diagram (C4 level)

```mermaid
flowchart TB
    subgraph Users["Users"]
        Planner["Planner (browser)"]
        Admin["Admin (browser)"]
    end

    subgraph Frontend["apps/frontend — Next.js 15 + Serwist PWA"]
        direction TB
        AppRouter["App Router<br/>(auth) · (planner) · (admin)"]
        Features["features/<br/>auth · intake · research · itinerary · booking · chat"]
        APIClient["lib/api + generated types"]
        PWA["Serwist SW + manifest<br/>network-only /api/v1/**"]
        AppRouter --> Features --> APIClient
        AppRouter --- PWA
    end

    subgraph Backend["apps/backend — Spring Boot 3"]
        direction TB
        Controllers["api/controller/<br/>thin HTTP routing"]
        Services["application/<br/>*Service — business logic"]
        Domain["domain/<br/>models · ports · algorithms"]
        Infra["infrastructure/<br/>JPA · auth · mail · vendors"]
        AI["ai/<br/>agents · tools · LangChain4j"]
        Controllers --> Services
        Services --> Domain
        Services --> Infra
        Services --> AI
    end

    subgraph Data["PostgreSQL + pgvector"]
        TKB["Travel Knowledge Base<br/>destination · poi · route · app"]
        AppData["App data<br/>user · trip · booking · chat"]
        Vectors["Embeddings<br/>destination_embedding · poi_embedding"]
    end

    subgraph External["External services"]
        LLM["Anthropic / OpenAI"]
        Firebase["Firebase (Gmail auth)"]
        GitHub["GitHub OAuth"]
        Resend["Resend (email)"]
        Vendors["Flight/hotel APIs<br/>(stub-first)"]
    end

    Planner -->|"HTTPS"| AppRouter
    Admin -->|"HTTPS"| AppRouter
    APIClient -->|"HTTP /api/v1/<br/>JWT httpOnly cookie"| Controllers
    Infra --> Data
    AI --> LLM
    AI --> TKB
    Infra --> Firebase
    Infra --> GitHub
    Infra --> Resend
    Infra --> Vendors
```

### 1.2 Backend hexagonal layers

```mermaid
flowchart LR
    subgraph API["api/"]
        Ctrl["controller"]
        DTO["dto"]
        Mapper["mapper"]
        OpenAPI["openapi"]
    end

    subgraph Application["application/"]
        TripSvc["TripService"]
        ResearchSvc["ResearchService"]
        ChatSvc["ChatOrchestrator"]
        BookingSvc["BookingService"]
    end

    subgraph DomainLayer["domain/"]
        Models["model"]
        Ports["port<br/>KnowledgePort · LlmPort · MailerPort"]
        Algo["algorithm<br/>DestinationRanker"]
    end

    subgraph Infrastructure["infrastructure/"]
        JPA["persistence/JPA"]
        Auth["auth adapters"]
        Mail["mail/Resend"]
        Knowledge["knowledge/PgVector"]
    end

    subgraph AILayer["ai/"]
        Agents["agent<br/>TravelResearchAgent"]
        Tools["tool<br/>ToolRegistry"]
        LC4j["langchain4j"]
    end

    Ctrl --> TripSvc
    Ctrl --> ResearchSvc
    Ctrl --> ChatSvc
    TripSvc --> Models
    TripSvc --> Ports
    ResearchSvc --> Ports
    ResearchSvc --> Agents
    ChatSvc --> Agents
    Agents --> Tools
    Tools --> Ports
    LC4j --> Agents
    Ports -.-> JPA
    Ports -.-> Auth
    Ports -.-> Mail
    Ports -.-> Knowledge
```

### 1.3 Data domains

```mermaid
erDiagram
    USER ||--o{ USER_IDENTITY : has
    USER ||--o{ TRIP : owns
    TRIP ||--o| TRIP_BRIEF : has
    TRIP ||--o{ TRIP_CLARIFICATION : may_have
    TRIP ||--o{ RESEARCH_JOB : triggers
    RESEARCH_JOB ||--o{ RANKED_RECOMMENDATION : produces
    TRIP ||--o| SELECTED_RECOMMENDATION : selects
    TRIP ||--o{ ITINERARY_DAY : contains
    ITINERARY_DAY ||--o{ ITINERARY_ITEM : has
    ITINERARY_ITEM ||--o{ ITINERARY_LEG : connects_via
    DESTINATION ||--o| DESTINATION_GUIDE : has
    DESTINATION ||--o{ DESTINATION_AREA : contains
    DESTINATION ||--o{ POI : has
    DESTINATION ||--o{ TRANSPORT_MODE : offers
    DESTINATION ||--o{ TRAVEL_APP : recommends
    ROUTE_SEGMENT }o--|| POI : from_to
    TRIP ||--o| CONVERSATION : has
    CONVERSATION ||--o{ MESSAGE : contains
    USER ||--o{ PLANNER_SESSION : has
```

---

## 2. Flow diagrams

### 2.1 MVP end-to-end user flow

```mermaid
flowchart TD
    Start([User opens app]) --> Auth{Authenticated?}
    Auth -->|No| SignUp[Sign up / Log in<br/>Local · Gmail · GitHub]
    SignUp --> Verify{Email verified?<br/>local only}
    Verify -->|No| Blocked[Blocked from planner]
    Verify -->|Yes| PlannerHome
    Auth -->|Yes| PlannerHome[Planner home /trips<br/>chat composer]

    PlannerHome --> ChatIntent[User: help me create a plan]
    ChatIntent --> LLMQuestions{Enough info?}
    LLMQuestions -->|No| AskQ[LLM asks 1-2 questions]
    AskQ --> ChatIntent
    LLMQuestions -->|Partial| CreateTrip[create_trip → DRAFT]
    CreateTrip --> UpdateBrief[update_trip_brief]

    UpdateBrief --> BriefValid{Brief complete?}
    BriefValid -->|No| Clarify[CLARIFICATION_NEEDED<br/>typed questions]
    Clarify --> AnswerClarify[answer_clarification]
    AnswerClarify --> BriefValid
    BriefValid -->|Yes| BriefComplete[BRIEF_COMPLETE]

    BriefComplete --> StartResearch[start_research<br/>POST 202 job_id]
    StartResearch --> AsyncJob[RESEARCH_QUEUED → RUNNING<br/>TravelResearchAgent + TKB]
    AsyncJob --> Poll[Frontend polls job status]
    Poll --> ResearchDone{Job complete?}
    ResearchDone -->|No| Poll
    ResearchDone -->|Yes| ResearchReady[RESEARCH_READY<br/>ranked recommendations]

    ResearchReady --> LLMRecommend[LLM summarizes options<br/>waits for confirmation]
    LLMRecommend --> UserSelect[User selects destination<br/>card or chat]
    UserSelect --> DestSelected[DESTINATION_SELECTED]

    DestSelected --> GenItinerary[generate_itinerary<br/>day timeline + legs + apps]
    GenItinerary --> ItineraryReady[ITINERARY_READY]

    ItineraryReady --> Enhance{User action}
    Enhance -->|Chat refine| PatchItinerary[patch_itinerary]
    PatchItinerary --> ItineraryReady
    Enhance -->|Book| SearchQuotes[search_booking_quotes]
    SearchQuotes --> BookingUI[C4 booking UI<br/>explicit confirm button]
    BookingUI --> Booked([BOOKED])
```

### 2.2 Chat tool orchestration flow

```mermaid
flowchart TD
    subgraph PlannerLevel["Planner level (no trip)"]
        PM[POST /api/v1/planner/chat/messages]
        PM --> PA[PlannerChatOrchestrator]
        PA --> CT{create_trip?}
        CT -->|Yes| NewTrip[Create trip DRAFT<br/>SSE trip_created]
        NewTrip --> Navigate[UI → /trips/id]
    end

    subgraph TripLevel["Trip level (status-gated)"]
        TM[POST /api/v1/trips/id/chat/messages]
        TM --> TA[TripChatOrchestrator]
        TA --> Status{trip.status}

        Status -->|DRAFT / CLARIFICATION_NEEDED| T1[update_trip_brief<br/>answer_clarification]
        Status -->|BRIEF_COMPLETE| T2[start_research]
        Status -->|RESEARCH_QUEUED / RUNNING| T3[Explain progress<br/>get_destination_guide]
        Status -->|RESEARCH_READY| T4[get_destination_guide<br/>select_recommendation]
        Status -->|DESTINATION_SELECTED| T5[generate_itinerary]
        Status -->|ITINERARY_READY+| T6[patch_itinerary<br/>get_route<br/>get_travel_apps<br/>search_booking_quotes]
    end

    Navigate --> TM
    T1 --> SSE[SSE stream<br/>tokens + tool events]
    T2 --> SSE
    T3 --> SSE
    T4 --> SSE
    T5 --> SSE
    T6 --> SSE
```

### 2.3 Knowledge-based AI flow (Retrieve → Reason → Ground)

```mermaid
flowchart LR
    Input["User query /<br/>TripBrief"] --> Retrieve

    subgraph Retrieve["RETRIEVE (TKB)"]
        SQL["SQL queries"]
        RAG["pgvector semantic search"]
        TKBTables["destination_guide · poi<br/>seasonality · price_history<br/>route_segment · travel_app"]
        SQL --> TKBTables
        RAG --> TKBTables
    end

    Retrieve --> Chunks["Retrieved chunks<br/>+ structured rows"]
    Chunks --> Reason

    subgraph Reason["REASON (LLM)"]
        Agent["TravelResearchAgent /<br/>TripPlannerAgent"]
        Web["WebSearchTool<br/>(events, advisories only)"]
        Agent --> Web
    end

    Reason --> Ground

    subgraph Ground["GROUNDED OUTPUT"]
        DTO["Typed DTOs<br/>RankedRecommendations<br/>TravelerGuide · Itinerary"]
        Refs["source_refs[]<br/>on every fact"]
        DTO --> Refs
    end
```

### 2.4 Booking state machine (C4)

```mermaid
stateDiagram-v2
    [*] --> DRAFT: create booking intent
    DRAFT --> QUOTED: search/quote
    QUOTED --> HELD: optional hold
    QUOTED --> CANCELLED: user decline / timeout
    HELD --> CONFIRMED: USER confirms in UI
    HELD --> CANCELLED: timeout / decline
    DRAFT --> FAILED: vendor error
    QUOTED --> FAILED: vendor error
    HELD --> FAILED: vendor error
    CONFIRMED --> [*]
    CANCELLED --> [*]
    FAILED --> [*]

    note right of CONFIRMED
        Idempotency key on confirm
        Price re-validated at commit
        No raw card/PAN stored
    end note
```

### 2.5 Authentication flow

```mermaid
flowchart TD
    subgraph Local["Local auth"]
        L1[POST /auth/register] --> L2[Resend verify email]
        L2 --> L3{email_verified?}
        L3 -->|No| L4[Blocked from planner]
        L3 -->|Yes| L5[POST /auth/login]
        L5 --> JWT[JWT in tp_session cookie]
    end

    subgraph Gmail["Gmail (Firebase)"]
        G1[Firebase signInWithPopup] --> G2[POST /auth/firebase idToken]
        G2 --> G3[Backend verifies via Admin SDK]
        G3 --> G4{New user?}
        G4 -->|Yes| G5[Create user + welcome email]
        G4 -->|No| G6[Load existing / link provider]
        G5 --> JWT
        G6 --> JWT
    end

    subgraph GitHub["GitHub OAuth"]
        GH1[GET /auth/oauth/github/start] --> GH2[GitHub authorize]
        GH2 --> GH3[Callback with code]
        GH3 --> GH4[Exchange + find/create user]
        GH4 --> JWT
    end

    JWT --> Trips[Redirect /trips]
```

---

## 3. Activity diagrams

### 3.1 C1 — Intake & clarification

```mermaid
flowchart TD
    start((Start)) --> A1[User describes trip<br/>chat or form]
    A1 --> A2[LLM calls update_trip_brief]
    A2 --> A3{TripBrief valid?}
    A3 -->|No| A4[Set CLARIFICATION_NEEDED<br/>return typed questions]
    A4 --> A5[User answers via chat<br/>or PUT brief/clarification]
    A5 --> A6[answer_clarification tool]
    A6 --> A3
    A3 -->|Yes| A7[Set BRIEF_COMPLETE]
    A7 --> A8[C2 research unlocked]
    A8 --> stop((End))
```

### 3.2 C2 — Async research job

```mermaid
flowchart TD
    start((Start)) --> B1[POST research/run]
    B1 --> B2[Return 202 job_id]
    B2 --> B3[Set RESEARCH_QUEUED]
    B3 --> B4[Background: TravelResearchAgent]
    B4 --> B5[Set RESEARCH_RUNNING]
    B5 --> B6[Agent tool loop ≤90s<br/>TKB + optional web]
    B6 --> B7{Success?}
    B7 -->|Yes| B8[Persist RankedRecommendations]
    B8 --> B9[Set RESEARCH_READY]
    B9 --> B10[Optional research-complete email]
    B10 --> B11[Frontend polls complete]
    B7 -->|No| B12[Set job failed<br/>error_code]
    B11 --> B13[User views recommendation cards]
    B13 --> B14[User clicks Plan this trip]
    B14 --> B15[POST selected-recommendation]
    B15 --> B16[Set DESTINATION_SELECTED]
    B16 --> stop((End))
    B12 --> stop
```

### 3.3 C3 — Itinerary generation

```mermaid
flowchart TD
    start((Start)) --> C1{DESTINATION_SELECTED?}
    C1 -->|No| C2[409 destination_not_selected]
    C1 -->|Yes| C3[generate_itinerary tool/API]
    C3 --> C4[Retrieve POIs, routes, apps from TKB]
    C4 --> C5[LLM builds day-by-day plan]
    C5 --> C6[Area-clustered days<br/>food slots from poi.category=food]
    C6 --> C7[Create itinerary_day + itinerary_item<br/>with scheduled_start/end]
    C7 --> C8[Create itinerary_leg between items<br/>transport_mode + locale apps]
    C8 --> C9[Validate source_refs on each item]
    C9 --> C10[Set ITINERARY_READY]
    C10 --> C11[UI: vertical timeline<br/>leg chips + app badges]
    C11 --> stop((End))
    C2 --> stop
```

### 3.4 C5 — Chat turn processing (SSE)

```mermaid
flowchart TD
    start((User sends message)) --> D1[POST chat/messages]
    D1 --> D2[Load conversation history<br/>trip.status · TripBrief · snapshots]
    D2 --> D3[TripPlannerAgent decides action]
    D3 --> D4{Tool call?}
    D4 -->|No| D5[Stream text response]
    D4 -->|Yes| D6{Tool allowed<br/>for status?}
    D6 -->|No| D7[Reject / explain limitation]
    D6 -->|Yes| D8[Execute tool via Service layer]
    D8 --> D9[Update trip state if applicable]
    D9 --> D10[Stream tool event + result]
    D10 --> D5
    D5 --> D11[Persist message]
    D11 --> D12[UI refreshes stepper/forms]
    D12 --> stop((End))
    D7 --> D5
```

### 3.5 Full planning lifecycle (swimlanes)

```mermaid
flowchart TB
    subgraph User["User"]
        U1[Open planner] --> U2[Chat intent]
        U2 --> U3[Answer questions]
        U3 --> U4[Confirm destination]
        U4 --> U5[Review itinerary]
        U5 --> U6[Confirm booking]
    end

    subgraph Frontend["Frontend (Next.js)"]
        F1[Chat composer + SSE] --> F2[Stepper sync]
        F2 --> F3[Poll research job]
        F3 --> F4[Research cards UI]
        F4 --> F5[Timeline view]
        F5 --> F6[Booking confirm button]
    end

    subgraph Backend["Backend (Spring Boot)"]
        B1[ChatOrchestrator] --> B2[TripService]
        B2 --> B3[ResearchService async]
        B3 --> B4[ItineraryService]
        B4 --> B5[BookingService]
    end

    subgraph AI["AI Layer"]
        A1[TripPlannerAgent] --> A2[TravelResearchAgent]
        A2 --> A3[Itinerary generation]
    end

    subgraph TKB["Travel Knowledge Base"]
        K1[KnowledgePort retrieve]
    end

    U2 --> F1 --> B1 --> A1
    U3 --> F1
    B3 --> A2 --> K1
    F3 --> B3
    U4 --> F4 --> B2
    A3 --> K1 --> B4
    U5 --> F5
    U6 --> F6 --> B5
```

---

## 4. Component map (frontend ↔ backend ↔ features)

| Feature | Frontend module | Backend service | AI agent/tools | Status gate |
|---|---|---|---|---|
| C1 Intake | `features/intake/` | `TripBriefService` | `update_trip_brief`, `answer_clarification` | `DRAFT`, `CLARIFICATION_NEEDED` |
| C2 Research | `features/research/` | `ResearchService` | `TravelResearchAgent`, `start_research` | `BRIEF_COMPLETE` → `RESEARCH_READY` |
| C3 Itinerary | `features/itinerary/` | `ItineraryService` | `generate_itinerary`, `patch_itinerary` | `DESTINATION_SELECTED` → `ITINERARY_READY` |
| C4 Booking | `features/booking/` | `BookingService` | `search_booking_quotes` (propose only) | `ITINERARY_READY`+ |
| C5 Chat | `features/chat/` | `ChatOrchestrator` | `TripPlannerAgent`, all status-gated tools | All statuses |

---

## 5. Related documents

| Document | Content |
|---|---|
| [`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md) | Master architecture, rules, NFRs |
| [`plans/USE-CASES.md`](../plans/USE-CASES.md) | Use case catalog, acceptance criteria |
| [`plans/TRAVEL-KNOWLEDGE-CATALOG.md`](../plans/TRAVEL-KNOWLEDGE-CATALOG.md) | TKB entity catalog |
| [`docs/UI-UX-DESIGN-SYSTEM.md`](UI-UX-DESIGN-SYSTEM.md) | Visual tokens, PWA presentation |
| [`docs/PLAN-COMPATIBILITY.md`](PLAN-COMPATIBILITY.md) | Post-merge plan compatibility review |
| [`docs/adr/`](adr/) | ADRs — Gradle, JWT, extensibility, auth, **PWA** |
