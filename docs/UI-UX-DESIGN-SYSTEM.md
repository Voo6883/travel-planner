# Travel Planner UI/UX Design System

> Version 1.0 · Status: normative design specification · Applies to web and installable PWA
>
> Stack: Next.js 15+ App Router, TypeScript, Tailwind CSS, Ant Design 5, and next-intl

This document is the visual and interaction contract for all Travel Planner interfaces.
Human contributors and AI models must use it as the source of truth when creating UI.
Product behavior remains governed by
[`plans/USE-CASES.md`](../plans/USE-CASES.md) and
[`plans/superpower/PLAN.md`](../plans/superpower/PLAN.md).

Authority is split deliberately: `PLAN.md` governs architecture, data flow, and product
behavior; this document governs exact visual tokens, responsive presentation, component
appearance, interaction states, and accessibility. If an illustrative visual value in
`PLAN.md` differs from this document, use this document.

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
| `foreground-subtle` | `#5F6F84` | `#94A3B8` | Metadata and placeholders |
| `border` | `#64748B` | `#94A3B8` | Control boundary; at least 3:1 against its surface |
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

#### Mode-aware role tokens

Components consume these role names instead of raw palette names.

| Role token | Light | Dark |
|---|---:|---:|
| `action-primary-fill` | `#0958D9` | `#0958D9` |
| `action-primary-fill-hover` | `#003EB3` | `#003EB3` |
| `action-primary-fill-active` | `#002C8C` | `#002C8C` |
| `action-primary-text` | `#0958D9` | `#69B1FF` |
| `action-primary-text-hover` | `#003EB3` | `#91CAFF` |
| `focus-ring` | `#1677FF` | `#69B1FF` |
| `selection-surface` | `#E6F4FF` | `#082F49` |
| `selection-border` | `#BAE0FF` | `#4096FF` |
| `selection-text` | `#0958D9` | `#BAE0FF` |
| `ai-accent-text` | `#006D75` | `#5EEAD4` |
| `ai-accent-surface` | `#E6FFFB` | `#042F2E` |
| `chat-user-surface` | `#0958D9` | `#0958D9` |
| `chat-user-text` | `#FFFFFF` | `#FFFFFF` |

Expose these names as CSS variables and semantic Tailwind utilities. For example,
`bg-action-primary-fill`, `text-action-primary-text`, and `bg-selection-surface`.
Dark mode changes the variables, not component class names.

#### Semantic usage

| UI role | Required value |
|---|---|
| Primary filled control | `action-primary-fill`; hover `action-primary-fill-hover` |
| Link | `action-primary-text`; inline prose links are also underlined |
| Selected item | `selection-surface`, `selection-text`, `selection-border` |
| Focus ring | 2 px `focus-ring` with 2 px surface offset |
| AI activity | `ai-accent-text` detail on `ai-accent-surface`; never a full teal page |
| Price or seasonal highlight | Amber detail; do not imply warning unless labeled |
| Disabled control | `surface-subtle`, `foreground-subtle`, 60% visual emphasis |
| Source/provenance | Neutral outlined treatment with source icon |

Do not place normal-size white text on `blue-500`. Filled primary buttons use
`action-primary-fill` to maintain readable contrast. Normal-size teal text uses
`teal-700` or `ai-accent-text`, never `teal-600` on `teal-50`.

### 3.2 Typography

Use **Inter** for all Latin text and the system sans fallback for other scripts.

`Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif`

| Style | Size / line height | Weight | Use |
|---|---|---:|---|
| `display` | 40 / 48 px | 700 | Marketing hero only |
| `h1` | 30 / 38 px | 700 | Page title on desktop |
| `h1-mobile` | 24 / 32 px | 700 | Page title below 768 px |
| `h2` | 24 / 32 px | 600 | Major section |
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
- Keep readable prose to 65–75 characters per line with the shared `max-w-prose` utility.
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

Never use off-grid spacing unless an Ant internal token requires it. Align neighboring
content to a common 4 px baseline.

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
- Use `object-cover` and a center crop. Text over photography requires a solid backing or
  tested overlay that preserves 4.5:1 text and 3:1 essential-graphic contrast for every crop.
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
| Base | 16 px | 24 px | Full |
| `md` | 24 px | 32 px | Full |
| `xl` | 32 px | 32 px | 1280 px maximum |

Default convention:

`mx-auto w-full max-w-7xl px-4 py-6 md:px-6 md:py-8 xl:px-8`

Vertical page content uses `flex flex-col gap-6`; major page groups may use `gap-8`.
This padding applies inside the flexible main area, not around the full planner shell.

### 4.3 Planner shell

#### Standard desktop, 1280–1439 px

- Left navigation: 240 px fixed.
- Top header: 64 px high.
- Persistent trip chat: 360 px fixed and collapsible.
- Main area receives all remaining width.
- A 1 px border separates chat from structured content.
- Trip stepper is horizontal beneath the header and above page content.

#### Wide desktop, 1440 px and wider

- Left navigation remains 240 px.
- Chat defaults to 400 px and may resize between 360 and 480 px.
- Main area remains at least 720 px.
- Resizing provides visible decrease, increase, and reset controls in addition to drag.
- The resize separator supports arrow keys in 16 px increments and announces its value.
- Persist the chosen width per device; reset returns to 400 px.

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
- Sticky regions must not cover focus, validation errors, toasts, or the final list item.
- Set scroll padding equal to sticky header/footer height and scroll focused controls fully into view.

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
An Error step means its latest operation failed and requires user action, such as failed
research, itinerary generation, or booking. Field validation alone does not mark a step Error.

Status gates are normative:

| Trip status | Brief | Research | Itinerary | Booking |
|---|---|---|---|---|
| `DRAFT`, `CLARIFICATION_NEEDED` | Current | Locked | Locked | Locked |
| `BRIEF_COMPLETE` | Complete | Current | Locked | Locked |
| `RESEARCH_QUEUED`, `RESEARCH_RUNNING` | Complete | Current | Locked | Locked |
| `RESEARCH_READY` | Complete | Current | Locked | Locked |
| `DESTINATION_SELECTED` | Complete | Complete | Current | Locked |
| `ITINERARY_READY` | Complete | Complete | Complete/current | Available |
| `BOOKING_IN_PROGRESS` | Complete | Complete | Complete | Current |
| `BOOKED` | Complete | Complete | Complete | Complete |
| `ARCHIVED` | Complete state retained | Available read-only | Available read-only | Available read-only |

Users may navigate to complete or available steps. Locked steps remain visible and explain
their prerequisite. An operation failure marks its step Error without changing the last
valid `trip.status`.

### 5.3 Breadcrumbs

- Use on admin detail screens and deeply nested settings only.
- Do not use on standard trip steps; the stepper provides location.
- On mobile, replace long breadcrumbs with a back action and short page title.

---

## 6. Component specifications

### 6.1 Buttons

| Variant | Use | Height |
|---|---|---:|
| Primary | One next-step action per region | 44 px |
| Default | Secondary action | 44 px |
| Text | Low-emphasis action | 44 px hit area |
| Link | Inline navigation only | 44 px hit area when standalone |
| Dashed | Optional add-another action | 44 px |
| Destructive primary | Confirmed destructive action in modal only | 44 px |

Rules:

- Radius is 8 px; horizontal padding is 16 px; gap between icon and label is 8 px.
- Labels start with a verb: “Start research”, “Plan this trip”, “Confirm booking”.
- Pending buttons keep their width, show a spinner, and use present-progress text where helpful.
- Disabled workflow actions include adjacent explanatory text, not only a tooltip.
- Visual, DOM, and keyboard order must match at every breakpoint. Never use CSS `order`
  to create a different focus sequence.

### 6.2 Inputs and forms

- Default control height is 44 px at every breakpoint.
- Label sits above the control with 8 px gap.
- Help text sits below with 8 px gap.
- Validation appears below the affected field; summary alert appears only for submit-wide errors.
- Required fields use text in the label or form intro; do not communicate with an asterisk alone.
- Use max-width 720 px for long forms.
- Use one column by default; two columns only at `lg` for strongly related short fields.
- Auto-save shows `Saving…`, `Saved`, or `Couldn’t save` near the page title.
- Debounced auto-save must not clear user input on failure.
- Chat updates never overwrite a focused or dirty field. Show an incoming-change notice,
  compare server and draft values, and let the user keep or accept the incoming value.
- Version conflicts pause auto-save and require field-level review before another save.

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

The selected destination uses the selection role tokens and a visible “Selected” label.

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
- Below 768 px, confirmation dialogs become bottom sheets and long forms become full-screen dialogs.
- Destructive confirmation names the affected item.
- Initial focus goes to the safest useful control; Escape closes unless an operation cannot be interrupted.
- Chat, navigation, and mobile filters use drawers rather than modal dialogs.
- Date ranges use a full-screen, one-month-at-a-time picker below 768 px. Select and
  multi-select popups remain within the viewport, use full available width, and keep
  selected tags to one line with a `+N` summary.

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
- Show confidence only when the API provides it. Never derive or invent thresholds in the UI.
- Render backend confidence labels verbatim through translated display labels. A backend
  low-confidence result uses a warning alert and asks the user to verify.
- Show freshness from source retrieval metadata in the relevant destination timezone.
  If the API marks content stale, pair the timestamp with a “May be outdated” warning.
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

The message history is a named `role="log"` region. Each message exposes sender,
content, timestamp, and delivery state in reading order. Repeated consecutive messages
may be visually grouped, but each retains an accessible sender label.

### 7.2 Messages

| Message | Alignment | Surface | Width |
|---|---|---|---|
| User | Right | `chat-user-surface`, `chat-user-text` | 85% mobile, 78% desktop max |
| Assistant | Left | `surface-subtle`, foreground | 92% mobile, 88% desktop max |
| System/status | Center or full row | Transparent/outlined | Content width |
| Error | Left | `destructive-surface` | Content width |

- Radius is 16 px with a 6 px corner toward the speaker.
- Message padding is 12 × 16 px.
- Timestamps and delivery state appear in 12 px text.
- Markdown is sanitized and uses readable heading, list, table, and link styles.
- Links inside prose remain underlined and receive an external-link label when applicable.
- Long tool activity collapses into a user-facing status such as “Comparing seasonal prices”.
- Never display internal tool identifiers or raw JSON.

### 7.3 Composer

- Minimum height is 48 px; expands to 160 px, then scrolls internally.
- Enter sends and Shift+Enter inserts a line break on desktop.
- On mobile, preserve the device keyboard’s multiline behavior and provide an explicit send button.
- While streaming, Send becomes Stop. The user can stop generation without losing prior content.
- Offline input may remain as an unsent draft but must not appear delivered.
- Suggestion chips scroll horizontally on mobile and wrap on desktop.
- Failed messages remain visible with “Not sent” and a retry action. Retry reuses the
  stable client message ID and must not create a duplicate message.

### 7.4 Streaming and handoff

- Show assistant content as it arrives without shifting existing messages.
- Announce completion through an `aria-live="polite"` region, not every token.
- Auto-follow only while the user is within 48 px of the bottom. Otherwise preserve scroll
  position and show a keyboard-focusable “New messages” action.
- Stopping marks the partial response “Stopped” and leaves it readable. Reconnecting marks
  the response “Interrupted” until the server confirms continuation or completion.
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
- Unverified local accounts remain outside the planner and see a verification gate with
  email address, resend state, change-account action, and support path.
- Forgot-password uses the same card shell. Submission shows a neutral confirmation
  regardless of whether the account exists.
- Reset-password handles valid, expired, and already-used links with a clear next action.
- Avoid travel photography behind forms; preserve contrast and focus.

### 8.3 Planner home `/trips`

#### First visit

- Center a welcoming heading and large chat composer within 720 px.
- Show 3–4 translated prompt suggestions.
- Explain that a trip is created when enough detail is available.
- Keep navigation minimal and avoid an empty dashboard grid.
- Before a trip exists, the planner-level chat uses the same message, streaming, failure,
  and accessibility patterns as trip chat, but its header says “Plan a new trip”.
- A vague opener produces one or two clarification questions in the same thread.
- The `trip_created` handoff preserves all messages, announces the new trip, and navigates
  only after the trip ID is confirmed.

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
- Typed clarification controls are deterministic: `money` uses amount plus currency,
  `choice` uses radio or Select by option count, `date` uses DatePicker, `number` uses
  InputNumber, and free text uses Input or TextArea.
- Chat renders the same typed clarification control inline with the assistant message.
  Submit disables while pending, then replaces the control with the translated answer.
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

#### Research failed

- Distinguish technical failure from a valid no-confident-result response.
- Show the translated error, preserved brief, retry action, and request reference when available.
- Retry starts a new job. Leaving and returning restores the failed state until the user retries.

#### Recommendations ready

- Header shows count, brief fit, and research freshness.
- Recommendation cards use one column mobile, two columns tablet, and three columns wide desktop.
- Rank alone does not determine selection; each card includes rationale and trade-offs.
- Traveler guide sections use Collapse: Overview, Why now, Areas, Food, Highlights,
  Practical, Mobility.
- “Download before you go” uses a checklist with local app name, purpose, and setup note.
- “Plan this trip” is the only primary action on each unselected card.
- “Run research again” is a secondary page action. Confirm that existing results remain
  available, then label historical result sets with run date and selected state.
- Selecting from chat requires the assistant to name the destination and wait for explicit
  confirmation, except when the user explicitly asks the planner to choose.

#### No confident result

- State that the knowledge base lacks a confident match.
- Offer “Adjust trip brief” and a secondary retry action.
- Do not show generic destination suggestions as factual results.

### 8.7 C3 Itinerary

#### Ready to generate

- Show the selected destination summary and one primary “Generate itinerary” action.
- Explain that generation uses the brief, grounded places, routes, and local transport knowledge.

#### Generating or failed

- Generation uses a destination-specific skeleton and real progress only when supplied.
- The user may leave; returning restores the active generation state.
- Failure preserves the selected destination and offers retry plus the request reference.

#### Itinerary ready

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
- Total price states whether taxes and fees are included; ambiguity blocks confirmation.
- Comparison uses consistent units and explicitly labels non-comparable terms.
- Selecting a quote opens a review step; it does not book.
- Confirmation modal repeats item, travelers/rooms, dates, total, currency, and cancellation terms.
- The final button says “Confirm and book” and is disabled while pending.
- Success shows provider reference and next steps.
- Expired price returns to review with old and new values clearly labeled.

Booking sub-states:

| State | UI behavior |
|---|---|
| `DRAFT` | Search or resume incomplete criteria |
| `QUOTED` | Compare current quotes; show expiry/freshness |
| `HELD` | Show hold expiry and review; confirmation available |
| Confirming | Lock duplicate submission; show non-cancellable pending state |
| Unknown after timeout | Reconcile status using the same idempotency key; never blind retry |
| `CONFIRMED` | Show provider reference, confirmed total, and next steps |
| `FAILED` | Show typed reason and safe retry only after reconciliation |
| `CANCELLED` | Show cancellation state and provider reference when available |

If price or terms change after review, return to review and require renewed consent.
Reloading or returning from a provider reconciles server state before enabling any action.

### 8.9 Settings

- Sections: Profile, Connected accounts, Preferences, Appearance, Account.
- Locale and theme changes preview immediately and persist explicitly.
- Connected providers show state and available action.
- Change password uses current password, new password, confirmation, and a success state
  that does not sign the user out unless required by backend policy.
- Resend verification is shown only for unverified local email state.
- Delete account is isolated in a danger zone and requires explicit confirmation.

### 8.10 Admin

- The `(admin)` shell uses the shared theme with a visible “Admin” context label.
- Non-admin users are redirected to a safe planner route and shown no admin navigation.
- Admin copy uses the `admin.json` namespace.
- Use compact density while preserving 44 px controls and 48 px rows.
- Page header provides title, result count, and approved actions.
- Filters sit above the table and collapse into a drawer on mobile.
- User detail uses description groups; password reset is a dedicated modal.
- Admin styling shares all tokens and components with planner; no separate theme.

---

## 9. PWA experience

### 9.1 Install prompt

- Never show an install prompt on first page load.
- Offer install after the user creates a trip or starts a later browser session.
- Use an inline card or account-menu action, not a blocking modal.
- Explain value: quick trip access, full-screen planning, and available offline content.
- Respect dismissal for at least 30 days.
- Hide the action when already installed or when the platform reports no install capability.
- On iOS, show translated manual “Add to Home Screen” steps instead of a false install button.
- A failed or cancelled install returns to the eligible state without a success message.

### 9.2 App identity

| Asset | Direction |
|---|---|
| App name | Travel Planner |
| Short name | Trips |
| Theme color | `#0958D9` |
| Light background | `#F8FAFC` |
| Dark background | `#0B1220` |
| Icon | Simple journey-pin mark; readable at 16 px; no text |
| Maskable icon | All essential artwork inside the central 80% of width and height |
| Splash | Solid theme background with centered mark |

### 9.3 Connectivity states

These states become required only after offline and synchronization behavior is approved
by an architecture decision. Until then, the safe baseline is detection, honest messaging,
local unsent chat drafts, and blocking all server-required actions.

| State | Presentation |
|---|---|
| Online | No persistent indicator |
| Reconnecting | Slim amber banner: “Reconnecting…” |
| Offline | Persistent neutral banner: “You’re offline” plus availability detail |
| Syncing | Slim informational banner with pending item count |
| Sync failed | Persistent error banner with review and retry actions |
| Back online | Success toast: “Back online” |
| Sync conflict | Blocking alert on affected content with review action |

- Never imply that research, chat, booking, or auto-save completed while offline.
- Previously available trip content may be labeled “Available offline” only when confirmed by implementation.
- Offline drafts display “Saved on this device” separately from server “Saved”.
- Booking confirmation is unavailable offline and must explain why.
- Synchronization never overwrites newer server or device edits silently. A conflict view
  shows both versions and allows field-level resolution before retry.

### 9.4 Update available

- Show a non-blocking toast/banner: “An update is ready”.
- Primary action is “Refresh now”; secondary action is “Later”.
- Disable “Refresh now” during unsaved edits, active streaming, synchronization, or
  booking confirmation and explain “Finish the current action before refreshing”.
- If the user chooses “Later”, keep the update available in the account menu.

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
- Store time in UTC. Itinerary items use the destination timezone; flight and transport
  endpoints use each location’s local time with zone abbreviation and `+1 day` when needed.
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

| Ant scope/token | Design token |
|---|---|
| Global `colorPrimary` | `action-primary-text` |
| Global `colorLink` | `action-primary-text` |
| Global `colorPrimaryHover` | `action-primary-text-hover` |
| Button primary background | `action-primary-fill` |
| Button primary hover | `action-primary-fill-hover` |
| Button primary active | `action-primary-fill-active` |
| Button primary text | `#FFFFFF` |
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
| `controlHeight` | 44 |
| `controlHeightLG` | 48 |

Dark mode uses Ant `darkAlgorithm` and the dark semantic values from this document.
Tailwind and Ant theme selection must use one shared mode state. Component token overrides
keep filled buttons dark enough for white text while links, tabs, and active text use the
lighter dark-mode `action-primary-text`.

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
│   ├── (marketing)/
│   ├── (auth)/
│   ├── (planner)/
│   └── (admin)/
├── components/
│   ├── layout/                  # AppShell, PageShell, PageHeader, TripStepper
│   └── ui/                      # Shared visual patterns and states
├── features/
│   ├── auth/
│   ├── admin/
│   ├── intake/                  # C1
│   ├── research/                # C2
│   ├── itinerary/               # C3
│   ├── booking/                 # C4
│   └── chat/                    # C5
├── lib/
│   ├── api/                     # Generated-client wrappers
│   ├── query/                   # TanStack Query setup
│   ├── i18n/                    # next-intl setup
│   └── utils/                   # cn and formatting
├── hooks/                       # Cross-feature layout context only
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
- Follow plans/superpower/PLAN.md §4.2.3, §4.2.6, §4.2.9, and §4.2.10 for architecture.
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
