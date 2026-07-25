# Travel Planner UI/UX Design System

> Version 1.0 · Status: normative design specification · Applies to web and installable PWA
>
> Stack: Next.js 15+ App Router, TypeScript, Tailwind CSS, Ant Design 5, and next-intl

This document is the visual and interaction contract for all Travel Planner interfaces.
Human contributors and AI models must use it as the source of truth when creating UI.
Product behavior remains governed by
[`plans/USE-CASES.md`](../plans/USE-CASES.md) and
[`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md).

PWA requirements here describe presentation and interaction only. Manifest, service
worker, caching, and offline data architecture require a separate implementation decision.

---

## 1. Design direction

### 1.1 Experience statement

Travel Planner should feel like a calm, knowledgeable local guide: trustworthy enough for
booking decisions, warm enough for inspiration, and focused enough for complex planning.

The UI is:

- **Chat-first:** conversation is the main planning surface.
- **Grounded:** recommendations visibly show rationale, cost, and sources.
- **Progressive:** the next useful action is clear without exposing the whole workflow.
- **Calm:** generous space, restrained color, short motion, and low visual noise.
- **Travel-aware:** destination imagery supports decisions but never replaces facts.
- **Safe:** irreversible and financial actions require explicit confirmation.

### 1.2 Visual character

| Attribute | Direction |
|---|---|
| Mood | Clear, optimistic, calm, credible |
| Primary color | Journey Blue |
| Accent color | Lagoon Teal |
| Supporting color | Sunset Amber |
| Surfaces | Cool neutral canvas with clean white cards |
| Shape | Soft corners, not pill-shaped by default |
| Depth | Borders first, subtle shadow second |
| Imagery | Natural destination photography with consistent crop and overlay |
| Density | Comfortable for planner; compact only for admin tables |

### 1.3 Product principles

1. **Conversation leads; structured UI confirms.**
   Chat, stepper, form, research, and itinerary must always show the same trip state.
2. **One screen, one primary action.**
   Secondary actions are visually quieter and never compete with the next workflow step.
3. **Evidence earns trust.**
   AI output labels sources, confidence, assumptions, price freshness, and generation state.
4. **Status is visible.**
   Long-running research, streaming, auto-save, offline state, and booking progress are never silent.
5. **Mobile is complete, not reduced.**
   Every core journey works at 320 px without hidden required actions or horizontal page scroll.

---

## 2. Rules for all AI-generated UI

The keywords **MUST**, **SHOULD**, and **MUST NOT** are normative.

### 2.1 MUST

- Use semantic tokens from this document; add a token before introducing a new visual value.
- Use Tailwind for layout, spacing, responsive behavior, typography, and custom presentation.
- Use Ant Design for behavior-rich widgets such as Form, Button, Select, DatePicker, Modal, and Table.
- Use Ant Design `Form` with `layout="vertical"` and `onValuesChange`.
- Use translated strings through next-intl; English and Malay layouts must both fit.
- Implement loading, error, empty, offline, disabled, hover, focus, and pending states where applicable.
- Keep the current trip status and the next available action visible.
- Use generated API types only when implementation begins.
- Preserve keyboard access, visible focus, reduced motion, and 44 × 44 px touch targets.

### 2.2 MUST NOT

- Use arbitrary hex colors, arbitrary spacing, CSS Modules, Sass, styled-components, or Emotion.
- restyle Ant components independently when a token or global override can solve the issue.
- Use gradients on standard controls, cards, navigation, or data surfaces.
- Use color alone to communicate state.
- place important instructions only in tooltips.
- show raw AI chain-of-thought, internal tool names, or unverified claims.
- use a destructive red button for a normal primary action.
- auto-confirm bookings or use optimistic success for financial actions.
- hide required actions behind hover on touch devices.

### 2.3 Decision order

When a design choice is not explicitly covered:

1. Reuse an existing pattern from this document.
2. Reuse an Ant Design interaction pattern and apply project tokens.
3. Add a shared pattern in `components/ui/` or `components/layout/`.
4. Propose a documented token or pattern addition.
5. Never solve the gap with a one-off component style.

---

## 3. Foundations

### 3.1 Color palette

Raw palette values may appear only in `design-tokens.ts`. Components use semantic names.

#### Journey Blue

| Token | Hex | Use |
|---|---:|---|
| `blue-50` | `#E6F4FF` | Selected and informational surface |
| `blue-100` | `#BAE0FF` | Strong selected border |
| `blue-200` | `#91CAFF` | Decorative highlight |
| `blue-300` | `#69B1FF` | Dark-theme primary text |
| `blue-400` | `#4096FF` | Hover accent |
| `blue-500` | `#1677FF` | Brand mark and focus ring |
| `blue-600` | `#0958D9` | Primary action and link |
| `blue-700` | `#003EB3` | Primary action hover |
| `blue-800` | `#002C8C` | Primary action active |
| `blue-900` | `#001D66` | Deep brand detail |

#### Lagoon Teal

| Token | Hex | Use |
|---|---:|---|
| `teal-50` | `#E6FFFB` | Positive AI and mobility surface |
| `teal-100` | `#B5F5EC` | Accent border |
| `teal-500` | `#13C2C2` | Decorative accent |
| `teal-600` | `#08979C` | Accent icon and text |
| `teal-700` | `#006D75` | Accent hover and accessible text |

#### Sunset Amber

| Token | Hex | Use |
|---|---:|---|
| `amber-50` | `#FFF7E6` | Warning and seasonal highlight surface |
| `amber-100` | `#FFE7BA` | Warning border |
| `amber-500` | `#FA8C16` | Decorative highlight |
| `amber-700` | `#AD4E00` | Accessible warning text |

#### Neutral and feedback

| Semantic token | Light | Dark | Purpose |
|---|---:|---:|---|
| `canvas` | `#F8FAFC` | `#0B1220` | App background |
| `surface` | `#FFFFFF` | `#111827` | Primary card and panel |
| `surface-subtle` | `#F1F5F9` | `#172033` | Secondary grouped surface |
| `surface-elevated` | `#FFFFFF` | `#1E293B` | Popover, modal, floating panel |
| `foreground` | `#0F172A` | `#F8FAFC` | Primary text |
| `foreground-muted` | `#475569` | `#CBD5E1` | Secondary text |
| `foreground-subtle` | `#64748B` | `#94A3B8` | Metadata and placeholders |
| `border` | `#CBD5E1` | `#475569` | Controls and strong divisions |
| `border-subtle` | `#E2E8F0` | `#334155` | Cards and separators |
| `success` | `#15803D` | `#4ADE80` | Completed and available |
| `success-surface` | `#F0FDF4` | `#052E16` | Success background |
| `warning` | `#AD4E00` | `#FDBA74` | Attention and stale data |
| `warning-surface` | `#FFF7E6` | `#431407` | Warning background |
| `destructive` | `#B91C1C` | `#F87171` | Error and destructive action |
| `destructive-surface` | `#FEF2F2` | `#450A0A` | Error background |
| `info` | `#0369A1` | `#7DD3FC` | Informational state |
| `info-surface` | `#F0F9FF` | `#082F49` | Informational background |
| `scrim` | `rgba(15,23,42,.56)` | `rgba(0,0,0,.68)` | Modal and drawer overlay |

#### Semantic usage

| UI role | Required value |
|---|---|
| Primary filled control | `blue-600`; hover `blue-700`; active `blue-800` |
| Link on light surface | `blue-600`; hover `blue-700` |
| Selected item | `blue-50` surface, `blue-600` text, `blue-100` border |
| Focus ring | 2 px `blue-500` with 2 px surface offset |
| AI activity | Teal icon/detail; never a full teal page |
| Price or seasonal highlight | Amber detail; do not imply warning unless labeled |
| Disabled control | `surface-subtle`, `foreground-subtle`, 60% visual emphasis |
| Source/provenance | Neutral outlined treatment with source icon |

Do not place normal-size white text on `blue-500`. Filled primary buttons use
`blue-600` to maintain readable contrast.

### 3.2 Typography

Use **Inter** for all Latin text and the system sans fallback for other scripts.

`Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`

| Style | Size / line height | Weight | Use |
|---|---|---:|---|
| `display` | 40 / 48 px | 700 | Marketing hero only |
| `h1` | 30 / 38 px | 700 | Page title on desktop |
| `h1-mobile` | 24 / 32 px | 700 | Page title below 768 px |
| `h2` | 24 / 32 px | 650 | Major section |
| `h3` | 20 / 28 px | 600 | Card group or panel |
| `title` | 16 / 24 px | 600 | Card and modal title |
| `body` | 16 / 24 px | 400 | Default content |
| `body-sm` | 14 / 20 px | 400 | Secondary content and controls |
| `label` | 14 / 20 px | 600 | Form and compact headings |
| `caption` | 12 / 16 px | 500 | Metadata, source, timestamp |
| `metric` | 28 / 34 px | 700 | Price and key trip metric |

Rules:

- Use sentence case for headings, labels, buttons, tabs, and navigation.
- Use tabular numerals for prices, dates, durations, and progress values.
- Keep readable prose to 65–75 characters per line.
- Do not use font size below 12 px.
- Use weight before color to establish hierarchy; avoid more than three weights on one screen.

### 3.3 Spacing

Use a 4 px base grid.

| Token | Value | Typical use |
|---|---:|---|
| `space-1` | 4 px | Icon-to-label micro gap |
| `space-2` | 8 px | Inline controls and metadata |
| `space-3` | 12 px | Compact card padding |
| `space-4` | 16 px | Default control/card spacing |
| `space-5` | 20 px | Comfortable component padding |
| `space-6` | 24 px | Section gap and desktop card padding |
| `space-8` | 32 px | Major section separation |
| `space-10` | 40 px | Page group separation |
| `space-12` | 48 px | Marketing and empty-state spacing |
| `space-16` | 64 px | Large layout separation |

Never use 6, 10, 14, 18, 22, or other off-grid spacing unless an Ant internal token
requires it. Align neighboring content to a common 4 px baseline.

### 3.4 Radius, border, and shadow

| Token | Value | Use |
|---|---:|---|
| `radius-sm` | 6 px | Tags, compact controls |
| `radius-md` | 8 px | Inputs and buttons |
| `radius-lg` | 12 px | Cards and alerts |
| `radius-xl` | 16 px | Modal, drawer, destination image card |
| `radius-full` | 9999 px | Avatar, status dot, chips only |
| `border-default` | 1 px | Controls, cards, dividers |
| `shadow-sm` | `0 1px 2px rgba(15,23,42,.06)` | Default raised card |
| `shadow-md` | `0 8px 24px rgba(15,23,42,.10)` | Popover and sticky composer |
| `shadow-lg` | `0 20px 48px rgba(15,23,42,.16)` | Modal and drawer |

Cards use a border plus `shadow-sm`. A card may increase to `shadow-md` on hover only
when the entire card is interactive.

### 3.5 Iconography and imagery

- Use one outline icon family throughout. Prefer Ant Design icons.
- Default icon sizes: 16 px inline, 20 px control, 24 px navigation, 32 px feature state.
- Pair unfamiliar icons with text. Icon-only controls require translated `aria-label`.
- Use destination photography at 16:9 for hero/card media and 4:3 for compact cards.
- Use `object-cover`, a center crop, and an optional bottom scrim only when text overlays an image.
- Always provide meaningful alt text; decorative images use empty alt text.
- Never use flags to represent language.
- Empty-state illustrations use Journey Blue, Lagoon Teal, and neutrals only.

### 3.6 Motion

| Motion | Duration | Easing |
|---|---:|---|
| Hover/focus color | 120 ms | `ease-out` |
| Expand/collapse | 180 ms | `ease-out` |
| Drawer/modal enter | 220 ms | `cubic-bezier(.2,.8,.2,1)` |
| Page-level reveal | 240 ms maximum | `ease-out` |
| Skeleton shimmer | 1.5 s loop | linear |

- Animate opacity and transform only; avoid layout animation for primary content.
- Streaming chat uses a subtle 6 px teal pulse, not a bouncing ellipsis.
- Respect `prefers-reduced-motion`; remove transforms and continuous pulse/shimmer.
- Never delay navigation or data display to complete animation.

---

## 4. Responsive layout

### 4.1 Breakpoints

Use the standard Tailwind breakpoints.

| Name | Minimum width | Intended layout |
|---|---:|---|
| Base | 0 px | Single-column phone |
| `sm` | 640 px | Large phone / small tablet |
| `md` | 768 px | Tablet |
| `lg` | 1024 px | Compact desktop |
| `xl` | 1280 px | Standard desktop |
| `2xl` | 1536 px | Wide desktop with capped content |

Design and verify at 320, 375, 768, 1024, 1280, and 1440 px.

### 4.2 Page shell

| Viewport | Horizontal padding | Vertical padding | Content width |
|---|---:|---:|---:|
| Base | 16 px | 20 px | Full |
| `md` | 24 px | 32 px | Full |
| `xl` | 32 px | 32 px | 1280 px maximum |

Default convention:

`mx-auto w-full max-w-7xl px-4 py-5 md:px-6 md:py-8 xl:px-8`

Vertical page content uses `flex flex-col gap-6`; major page groups may use `gap-8`.

### 4.3 Planner shell

#### Desktop, 1280 px and wider

- Left navigation: 240 px fixed.
- Top header: 64 px high.
- Main area: flexible, minimum 560 px.
- Persistent trip chat: 400 px, resizable only between 360 and 480 px.
- A 1 px border separates chat from structured content.
- Trip stepper is horizontal beneath the header and above page content.

#### Compact desktop, 1024–1279 px

- Left navigation collapses to 72 px icon rail.
- Chat panel defaults to 360 px and can collapse to a labeled floating button.
- Step labels may shorten, but icon, state, and accessible name remain.

#### Tablet, 768–1023 px

- Navigation becomes a drawer opened from the header.
- Structured content remains the default view.
- Chat opens as a right drawer at 88% width, maximum 560 px.
- Stepper becomes horizontally scrollable and keeps the current step in view.

#### Mobile, below 768 px

- Header: 56 px with menu, trip title, and chat action.
- Bottom navigation: 64 px plus safe-area inset.
- Chat is a full-screen layer with its own 56 px header.
- Stepper is a compact progress control: current step, `Step N of 4`, and next/previous actions.
- Sticky primary actions sit above bottom navigation and safe-area inset.
- No two-column forms or card grids.

### 4.4 Safe areas and viewport behavior

- Apply `env(safe-area-inset-*)` to full-screen PWA headers, bottom navigation, drawers,
  and sticky composers.
- Use dynamic viewport units for full-height mobile panels; do not rely on `100vh`.
- Keep focused form fields and the chat composer visible when the virtual keyboard opens.
- Sticky regions must not cover validation errors, toasts, or the final list item.

---

## 5. Navigation and information architecture

### 5.1 Primary navigation

Planner navigation order:

1. Trips
2. Explore only if a future use case is approved
3. Settings
4. Help

User profile, locale, theme, and sign-out live in the account menu. Admin is shown only
to authorized users and is visually separated from planner navigation.

### 5.2 Trip stepper

Visible steps:

1. Brief
2. Research
3. Itinerary
4. Booking

| State | Visual treatment | Behavior |
|---|---|---|
| Complete | Success check, `success` text | Navigable |
| Current | Blue icon/fill, strong label | `aria-current="step"` |
| Available | Neutral icon, foreground label | Navigable |
| Locked | Lock icon, muted label | Disabled; reason available in text |
| Error | Error icon and label | Navigable to resolution |

Chat is persistent and is not a fifth step. It spans and controls all four steps.

### 5.3 Breadcrumbs

- Use on admin detail screens and deeply nested settings only.
- Do not use on standard trip steps; the stepper provides location.
- On mobile, replace long breadcrumbs with a back action and short page title.

---

## 6. Component specifications

### 6.1 Buttons

| Variant | Use | Height |
|---|---|---:|
| Primary | One next-step action per region | 40 px default, 44 px mobile |
| Default | Secondary action | 40 px |
| Text | Low-emphasis action | 36–40 px |
| Link | Inline navigation only | Content height |
| Dashed | Optional add-another action | 40 px |
| Destructive primary | Confirmed destructive action in modal only | 40 px |

Rules:

- Radius is 8 px; horizontal padding is 16 px; gap between icon and label is 8 px.
- Labels start with a verb: “Start research”, “Plan this trip”, “Confirm booking”.
- Pending buttons keep their width, show a spinner, and use present-progress text where helpful.
- Disabled workflow actions include adjacent explanatory text, not only a tooltip.
- Button groups wrap on mobile with the primary action first visually and last in DOM only
  when required for natural keyboard order.

### 6.2 Inputs and forms

- Default control height: 40 px desktop and 44 px mobile.
- Label sits above the control with 8 px gap.
- Help text sits below with 6 px gap.
- Validation appears below the affected field; summary alert appears only for submit-wide errors.
- Required fields use text in the label or form intro; do not communicate with an asterisk alone.
- Use max-width 720 px for long forms.
- Use one column by default; two columns only at `lg` for strongly related short fields.
- Auto-save shows `Saving…`, `Saved`, or `Couldn’t save` near the page title.
- Debounced auto-save must not clear user input on failure.

Ant component choices:

| Need | Component |
|---|---|
| Text | `Input` / `Input.TextArea` |
| Choice, 2–4 items | `Radio.Group` or segmented control |
| Choice, many items | `Select` |
| Multiple interests | `Select` with multi-select or checkbox group |
| Date range | `DatePicker.RangePicker` |
| Money | `InputNumber` plus currency `Select` |
| Yes/no preference | `Switch` only for immediate settings; otherwise radio |

### 6.3 Cards

Default card:

- `surface` background, `border-subtle`, 12 px radius, 24 px desktop padding.
- Mobile padding is 16 px.
- Header-to-body gap is 16 px; body-to-actions gap is 20 px.
- Only show hover elevation when the full card is actionable.
- If only one control is actionable, the card itself is not clickable.

Destination recommendation card order:

1. Image with rank badge.
2. Destination and country.
3. Match summary and `why now`.
4. Cost estimate and trip-fit tags.
5. Source count and freshness.
6. Expandable traveler guide.
7. “Plan this trip” primary action.

The selected destination uses a blue selected surface and a visible “Selected” label.

### 6.4 Tags, badges, and status

- Use tags for descriptive properties: “Food”, “Low crowds”, “Budget fit”.
- Use badges for count or compact status.
- Maximum three tags in a compact card; use “+N” for the remainder.
- Status labels pair icon, text, and color.
- Use title case for named statuses shown to users, not raw enum values.

Trip status mapping:

| System status | User label | Tone |
|---|---|---|
| `DRAFT` | Building brief | Neutral |
| `CLARIFICATION_NEEDED` | Needs your input | Warning |
| `BRIEF_COMPLETE` | Ready to research | Info |
| `RESEARCH_QUEUED` | Research queued | Info |
| `RESEARCH_RUNNING` | Researching | Teal activity |
| `RESEARCH_READY` | Recommendations ready | Success |
| `DESTINATION_SELECTED` | Destination selected | Success |
| `ITINERARY_READY` | Itinerary ready | Success |
| `BOOKING_IN_PROGRESS` | Booking in progress | Warning |
| `BOOKED` | Booked | Success |
| `ARCHIVED` | Archived | Neutral |

### 6.5 Alerts, messages, and notifications

| Pattern | Use |
|---|---|
| Inline field error | One invalid field |
| Alert | Persistent page/section issue or important instruction |
| Toast | Short confirmation that needs no action |
| Modal | Explicit confirmation or focused blocking task |
| Notification center | Background completion such as research ready |

- Toasts appear top-right on desktop and top-center below the safe-area header on mobile.
- Default duration is 4 seconds; actionable or critical feedback persists.
- Errors state what happened, impact, and recovery action.
- Do not show both a toast and an alert for the same event.

### 6.6 Modal and drawer

- Modal widths: 480 px confirmation, 640 px form, 800 px detailed comparison.
- Mobile modals become bottom sheets or full-screen dialogs when content is long.
- Destructive confirmation names the affected item.
- Initial focus goes to the safest useful control; Escape closes unless an operation cannot be interrupted.
- Chat, navigation, and mobile filters use drawers rather than modal dialogs.

### 6.7 Table and list

- Planner experiences prefer cards or lists. Admin uses Ant Design Table.
- Table header is `surface-subtle`; row height is at least 48 px.
- Keep the primary identifier in the first column and row action menu in the last.
- On mobile, convert essential table data to stacked list items; do not squeeze columns.
- Lists over 100 visible rows use virtualization as required by the architecture plan.

### 6.8 Skeleton, empty, error, and offline states

Every data screen provides:

| State | Required content |
|---|---|
| Initial loading | Skeleton matching final layout; no spinner-only blank page |
| Background refresh | Existing content remains; small inline progress |
| Empty | Specific title, explanation, and one next action when available |
| Error | Plain-language message, retry action, request reference if available |
| Partial data | Render safe data and label unavailable sections |
| Offline | Offline banner plus what remains available |
| Permission denied | Explain access boundary; offer safe navigation |

Skeletons use the same number and approximate size of final elements. Do not show skeleton
for interactions expected to finish in under 300 ms.

### 6.9 Source and confidence treatment

- Factual recommendation and itinerary content shows a compact “Sources” action.
- Source details include source name, retrieved date, and external-link indicator.
- Low-confidence content uses a warning alert and asks the user to verify; never fabricate fallback facts.
- AI-generated summaries use a subtle sparkle icon and “AI-assisted” accessible label where provenance matters.
- Do not use an AI label on standard deterministic UI.

---

## 7. Chat system

### 7.1 Chat panel anatomy

1. Header: trip title, connection status, collapse/close.
2. Scrollable message history.
3. New-message indicator when the user is away from the bottom.
4. Contextual suggestion chips.
5. Composer with attachment slot reserved for future use.
6. Send/stop action and concise privacy note when needed.

### 7.2 Messages

| Message | Alignment | Surface | Width |
|---|---|---|---|
| User | Right | `blue-600`, white text | 85% mobile, 78% desktop max |
| Assistant | Left | `surface-subtle`, foreground | 92% mobile, 88% desktop max |
| System/status | Center or full row | Transparent/outlined | Content width |
| Error | Left | `destructive-surface` | Content width |

- Radius is 16 px with a 6 px corner toward the speaker.
- Message padding is 12 × 16 px.
- Timestamps and delivery state appear in 12 px text.
- Markdown is sanitized and uses readable heading, list, table, and link styles.
- Long tool activity collapses into a user-facing status such as “Comparing seasonal prices”.
- Never display internal tool identifiers or raw JSON.

### 7.3 Composer

- Minimum height is 48 px; expands to 160 px, then scrolls internally.
- Enter sends and Shift+Enter inserts a line break on desktop.
- On mobile, preserve the device keyboard’s multiline behavior and provide an explicit send button.
- While streaming, Send becomes Stop. The user can stop generation without losing prior content.
- Offline input may remain as an unsent draft but must not appear delivered.
- Suggestion chips scroll horizontally on mobile and wrap on desktop.

### 7.4 Streaming and handoff

- Show assistant content as it arrives without shifting existing messages.
- Announce completion through an `aria-live="polite"` region, not every token.
- When `trip_created` occurs, preserve the conversation and show a brief transition:
  “Trip created — continuing in your planner”.
- Research progress appears as a compact progress card in chat and in the Research screen.
- Booking suggestions link to the Booking screen; chat never shows a final confirmation control.

---

## 8. Screen blueprints

### 8.1 Marketing landing

- Hero: short value proposition, chat-first planning message, primary sign-up CTA.
- Supporting content: three steps—Describe, Discover, Go.
- Trust section: grounded sources, human-confirmed booking, secure account.
- Use one destination image or a composed product preview, not a carousel.
- Mobile puts the CTA before supporting imagery.

### 8.2 Authentication

- Centered card, 420 px maximum width, on `canvas`.
- Brand mark and concise heading above form.
- Social providers first, divider with “or”, then local credentials.
- Password requirements appear before failure.
- Email verification state replaces the form with clear resend and back actions.
- Avoid travel photography behind forms; preserve contrast and focus.

### 8.3 Planner home `/trips`

#### First visit

- Center a welcoming heading and large chat composer within 720 px.
- Show 3–4 translated prompt suggestions.
- Explain that a trip is created when enough detail is available.
- Keep navigation minimal and avoid an empty dashboard grid.

#### Returning user

- Chat composer remains the primary first region.
- “Continue planning” lists up to three recent trips with destination, dates, status, and next action.
- Remaining trips appear in a responsive card grid with search and status filter.
- Archived trips are visually quiet and excluded by default.

### 8.4 Trip overview

- Header: trip name, date range, party summary, status, overflow actions.
- Next-action card appears first.
- Four-step progress summary follows.
- Destination, brief, itinerary, and app checklist appear only when available.
- Persistent chat remains accessible at every breakpoint.

### 8.5 C1 Brief

- Intro states that chat and form stay synchronized.
- Group fields: destination, dates, travelers, budget, interests, pace, constraints.
- “Surprise me” visibly disables destination selection and explains the effect.
- Clarification questions appear in a warning-toned section above unresolved fields.
- Auto-save status appears in the header; no redundant Save button unless recovery requires one.
- “Start research” unlocks only at `BRIEF_COMPLETE`, with missing requirements listed otherwise.

### 8.6 C2 Research

#### Ready to start

- Brief summary and one primary “Start research” action.
- Explain expected output, grounding, and that the user can leave while it runs.

#### Queued or running

- Progress card with current user-facing phase, percentage when real, and elapsed state.
- Use indeterminate progress when the backend provides no percentage; never invent progress.
- Keep chat available and permit safe navigation away.

#### Recommendations ready

- Header shows count, brief fit, and research freshness.
- Recommendation cards use one column mobile, two columns tablet, and three columns wide desktop.
- Rank alone does not determine selection; each card includes rationale and trade-offs.
- Traveler guide sections use Collapse: Overview, Why now, Areas, Food, Highlights,
  Practical, Mobility.
- “Download before you go” uses a checklist with local app name, purpose, and setup note.
- “Plan this trip” is the only primary action on each unselected card.

#### No confident result

- State that the knowledge base lacks a confident match.
- Offer “Adjust trip brief” and a secondary retry action.
- Do not show generic destination suggestions as factual results.

### 8.7 C3 Itinerary

- Desktop uses day tabs or a sticky day rail plus a vertical timeline.
- Mobile uses a day selector followed by one full-width timeline.
- Each timeline item shows time, duration, place, category, area, source, and optional cost.
- Route legs sit between items as compact chips/cards with mode icon, duration,
  instructions, cost band, and local app.
- Food items are visually distinguished with an icon, not a separate color system.
- Keep day-level actions near the day heading; full regeneration belongs in page actions.
- Regeneration requires scope confirmation and preserves the current view while pending.

### 8.8 C4 Booking

- Separate Flights and Stays with tabs only when both contain content.
- Quote cards show total price first, then provider, conditions, and freshness.
- Comparison uses consistent units and explicitly labels non-comparable terms.
- Selecting a quote opens a review step; it does not book.
- Confirmation modal repeats item, travelers/rooms, dates, total, currency, and cancellation terms.
- The final button says “Confirm and book” and is disabled while pending.
- Success shows provider reference and next steps.
- Expired price returns to review with old and new values clearly labeled.

### 8.9 Settings

- Sections: Profile, Connected accounts, Preferences, Appearance, Account.
- Locale and theme changes preview immediately and persist explicitly.
- Connected providers show state and available action.
- Delete account is isolated in a danger zone and requires explicit confirmation.

### 8.10 Admin

- Use compact density while preserving 40 px controls and 48 px rows.
- Page header provides title, result count, and approved actions.
- Filters sit above the table and collapse into a drawer on mobile.
- User detail uses description groups; password reset is a dedicated modal.
- Admin styling shares all tokens and components with planner; no separate theme.

---

## 9. PWA experience

### 9.1 Install prompt

- Never show an install prompt on first page load.
- Offer install after the user creates a trip or returns for a second session.
- Use an inline card or account-menu action, not a blocking modal.
- Explain value: quick trip access, full-screen planning, and available offline content.
- Respect dismissal for at least 30 days.

### 9.2 App identity

| Asset | Direction |
|---|---|
| App name | Travel Planner |
| Short name | Trips |
| Theme color | `#0958D9` |
| Light background | `#F8FAFC` |
| Dark background | `#0B1220` |
| Icon | Simple journey-pin mark; readable at 16 px; no text |
| Maskable icon | Mark centered inside 20% safe zone |
| Splash | Solid theme background with centered mark |

### 9.3 Connectivity states

| State | Presentation |
|---|---|
| Online | No persistent indicator |
| Reconnecting | Slim amber banner: “Reconnecting…” |
| Offline | Persistent neutral banner: “You’re offline” plus availability detail |
| Back online | Success toast: “Back online” |
| Sync conflict | Blocking alert on affected content with review action |

- Never imply that research, chat, booking, or auto-save completed while offline.
- Previously available trip content may be labeled “Available offline” only when confirmed by implementation.
- Offline drafts display “Saved on this device” separately from server “Saved”.
- Booking confirmation is unavailable offline and must explain why.

### 9.4 Update available

- Show a non-blocking toast/banner: “An update is ready”.
- Primary action is “Refresh now”; secondary action is “Later”.
- Do not refresh during unsaved form edits, active streaming, or booking confirmation.

### 9.5 Standalone mode

- Preserve in-app back navigation because browser controls may be absent.
- External sources open with a clear external-link treatment.
- Account, connectivity, and update status remain reachable from the app header.
- Apply safe-area insets and test portrait and landscape orientation.

---

## 10. Accessibility

Target **WCAG 2.2 AA** for all core journeys.

### 10.1 Required checks

- Normal text contrast is at least 4.5:1; large text and essential graphics at least 3:1.
- Keyboard focus is always visible and follows reading order.
- All functionality is available by keyboard without timing-dependent gestures.
- Touch targets are at least 44 × 44 px.
- Headings form a logical hierarchy with one page `h1`.
- Forms have persistent labels, programmatic errors, and error summary where useful.
- Dialogs trap focus, have an accessible name, and restore focus on close.
- Status changes use appropriate `aria-live` behavior.
- Stepper, progress, tabs, timeline, and chat expose semantic state.
- Color-coded status always includes an icon and text.
- Zoom at 200% does not hide content or actions.
- Reduced-motion mode removes nonessential animation.
- English and Malay text enlargement does not truncate required actions.

### 10.2 Focus and skip behavior

- Provide “Skip to main content” as the first focusable element.
- Trip pages also provide a keyboard path to chat and back to structured content.
- Opening mobile chat moves focus to the chat heading, not directly to the composer.
- After form errors, focus the error summary or first invalid field.
- After destination selection, announce success before moving to the next step.

---

## 11. Content and localization

### 11.1 Voice

- Helpful, direct, and calm.
- Prefer “We couldn’t save your changes” over “Mutation failed”.
- Prefer “Researching seasonal prices” over internal agent/tool terminology.
- State uncertainty directly: “We don’t have enough reliable information”.
- Avoid exaggerated claims such as “perfect trip” or “best ever”.

### 11.2 UI copy rules

- Buttons use 2–4 words and begin with a verb.
- Titles describe the task or result, not the component.
- Empty states explain why the page is empty and what to do next.
- Errors include recovery when recovery exists.
- Confirmation copy names the action and consequence.
- Do not use ellipsis in labels except to indicate an action opens a follow-up dialog.

### 11.3 Formatting

- Use `Intl` for date, time, number, currency, and relative time.
- Display currency code when ambiguity is possible.
- Store time in UTC and show the traveler-relevant timezone.
- Use localized date order; do not hardcode `MM/DD/YYYY`.
- Keep original local place and app names where useful, followed by translated or Latin name.
- All visible strings, alt text, aria labels, toasts, and errors use next-intl.

---

## 12. Tailwind and Ant Design alignment

### 12.1 Ownership

| Concern | Owner |
|---|---|
| Grid, flex, position, width, gap, padding, responsive | Tailwind |
| Typography and semantic color utilities | Tailwind tokens |
| Form behavior, validation shell, controls | Ant Design |
| Button, modal, drawer, table, tabs, collapse | Ant Design |
| Base Ant appearance | `ConfigProvider` tokens and global Tailwind layer |
| One-off conditional state | `cn()` and semantic Tailwind classes |

### 12.2 Required Ant token mapping

| Ant token | Design token |
|---|---|
| `colorPrimary` | `blue-600` |
| `colorPrimaryHover` | `blue-700` |
| `colorPrimaryActive` | `blue-800` |
| `colorInfo` | `info` |
| `colorSuccess` | `success` |
| `colorWarning` | `warning` |
| `colorError` | `destructive` |
| `colorText` | `foreground` |
| `colorTextSecondary` | `foreground-muted` |
| `colorBgBase` | `canvas` |
| `colorBgContainer` | `surface` |
| `colorBorder` | `border` |
| `colorBorderSecondary` | `border-subtle` |
| `borderRadius` | 8 |
| `borderRadiusLG` | 12 |
| `fontFamily` | Inter stack |
| `controlHeight` | 40 |
| `controlHeightLG` | 44 |

Dark mode uses Ant `darkAlgorithm` and the dark semantic values from this document.
Tailwind and Ant theme selection must use one shared mode state.

### 12.3 Global override policy

Global overrides should standardize:

- Button weight, radius, focus ring, and control height.
- Input, Select, DatePicker, and InputNumber radius and focus.
- Card border, radius, padding, and shadow.
- Modal and Drawer radius, header spacing, and mobile behavior.
- Form label typography and error spacing.
- Table header surface, row height, and focus.
- Tabs active color and minimum touch target.

Do not target generated Ant DOM structure more deeply than needed. Prefer component
tokens and semantic `classNames` APIs over fragile descendant selectors.

---

## 13. Theme modes

The app supports:

- `system` by default
- `light`
- `dark`

Theme selection lives in Settings and the account menu. Switching theme must not flash
the wrong mode during hydration.

Dark mode rules:

- Preserve information hierarchy; do not simply invert colors.
- Use borders more than shadows.
- Destination images reduce brightness slightly only when text overlays them.
- Do not use pure black backgrounds or pure white large surfaces.
- Charts and status colors use the dark semantic palette and remain distinguishable.

---

## 14. Implementation structure

When frontend implementation begins, use the locked repository structure:

```text
apps/frontend/src/
├── app/                         # Thin route composition only
├── components/
│   ├── layout/                  # AppShell, PageShell, PageHeader, TripStepper
│   └── ui/                      # Shared visual patterns and states
├── features/
│   ├── intake/                  # C1
│   ├── research/                # C2
│   ├── itinerary/               # C3
│   ├── booking/                 # C4
│   └── chat/                    # C5
├── styles/
│   ├── design-tokens.ts         # Only raw visual values
│   ├── ant-theme.ts             # Semantic values mapped to Ant
│   └── globals.css              # Tailwind layers and global Ant overrides
└── locales/                     # en and ms namespaces
```

Shared visual patterns belong in `components/`; feature-specific compositions stay inside
their feature. Do not import one feature from another.

---

## 15. AI handoff template

Use this block when asking another model to create a screen:

```text
Implement [screen] for Travel Planner.

Design authority:
- Follow docs/UI-UX-DESIGN-SYSTEM.md exactly.
- Follow plans/superpower/PLAN.md §4.2 for architecture.
- Follow plans/USE-CASES.md for behavior and status gates.

Required:
- Next.js App Router, strict TypeScript, Tailwind CSS, and Ant Design 5.
- Ant Form with onValuesChange for forms.
- Semantic design tokens only; no arbitrary values or CSS Modules.
- Responsive at 320, 375, 768, 1024, 1280, and 1440 px.
- Light and dark themes.
- Loading, error, empty, offline, disabled, focus, and pending states.
- WCAG 2.2 AA and translated user-facing strings.
- Thin page; feature component → hook → API layer.

Before finishing:
- Compare the result to the screen blueprint and component specifications.
- Verify keyboard, responsive, theme, localization, and status behavior.
- Reject any design choice that conflicts with the design authority.
```

---

## 16. Review checklist

### Visual consistency

- [ ] Uses Journey Blue as the primary action color.
- [ ] Uses semantic tokens with no arbitrary color or spacing.
- [ ] Uses Inter typography and the defined type scale.
- [ ] Uses the 4 px spacing grid and defined radii.
- [ ] Uses borders and restrained shadows.
- [ ] Matches light and dark theme rules.

### Interaction

- [ ] One clear primary action per region.
- [ ] Trip status and next action are visible.
- [ ] Pending, disabled, success, and failure states are explicit.
- [ ] Booking requires explicit confirmation.
- [ ] Chat and structured state remain synchronized.

### Responsive and PWA

- [ ] Core journey works at all required widths.
- [ ] Touch targets are at least 44 × 44 px.
- [ ] Safe areas and virtual keyboard are handled.
- [ ] Offline, reconnecting, install, and update states follow this document.
- [ ] Standalone mode retains navigation and external-link clarity.

### Accessibility and content

- [ ] Meets WCAG 2.2 AA contrast and keyboard requirements.
- [ ] Focus order, labels, errors, status announcements, and reduced motion are verified.
- [ ] No required meaning relies on color, hover, or icon alone.
- [ ] All user-facing content is translated.
- [ ] Dates, times, money, and local names are locale-aware.

### Architecture

- [ ] Tailwind owns layout and Ant owns behavior-rich widgets.
- [ ] Ant tokens map to the same semantic token source as Tailwind.
- [ ] No CSS Modules, styled-components, arbitrary values, or deep fragile overrides.
- [ ] Shared patterns are reused; feature-specific UI remains in its feature.

---

## 17. Definition of design done

A screen is design-complete only when:

1. Its normal, loading, empty, error, offline, and pending states are specified.
2. Its phone, tablet, compact desktop, and desktop layouts are unambiguous.
3. Primary, secondary, destructive, and disabled actions are correctly prioritized.
4. Trip status gates and chat synchronization match the use cases.
5. Light, dark, keyboard, reduced-motion, English, and Malay behavior are covered.
6. No value or pattern is invented outside this system without updating this document.

