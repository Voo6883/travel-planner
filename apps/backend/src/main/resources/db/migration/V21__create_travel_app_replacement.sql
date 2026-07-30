-- V21 — "the app you already have does not work here; install this instead."
--
-- Closes the schema gap the 2026-07-29 review raised as finding #5. V16 gave every country an app
-- pack, and that is enough to answer "what should I install for Japan". It cannot answer the
-- question the product actually needs answered, which is the *negative* one:
--
--     A traveller opens the China app pack. They already have Uber. Uber does not operate there.
--     Nothing in `travel_app` can say so, so the pack lists Didi beside an Uber the traveller will
--     open at the airport and find empty.
--
-- tasks/17 calls this "suppress inactive global alternatives", and until this table exists there is
-- no data behind that phrase — only an LLM being asked to remember which apps work where, which is
-- exactly the invented-fact failure PLAN §4.1.0 forbids.
--
-- ============================================================================================
-- WHY THE REPLACED APP IS A KEY AND NOT A FOREIGN KEY
-- ============================================================================================
-- The obvious shape is `replaced_app_id uuid REFERENCES travel_app (id)`. It is wrong, because
-- `travel_app` is a table of apps *to install in a country*, and Uber-in-China is the opposite of
-- that: it is a globally-held app that this market does not support. Putting it in `travel_app`
-- would mean seeding a row whose entire purpose is never to be shown, and every query that lists a
-- country's pack would then need to remember to exclude it. One forgotten `WHERE` and the pack
-- recommends installing the app the row exists to warn about.
--
-- So the replaced side is `replaced_app_key` — a stable slug for a globally-known app (`uber`,
-- `whatsapp`, `google-maps`). It is deliberately not scoped to a country: `uber` means the same
-- product everywhere, which is the property that makes the suppression join work at all.
--
-- ============================================================================================
-- WHY country_code IS NOT A COLUMN HERE
-- ============================================================================================
-- The review's sketch included it. It is omitted on purpose: `travel_app.country_code` already
-- holds it, reachable through `local_app_id` in one indexed lookup. A copy here would be a second
-- source of truth for the same fact, and the two can disagree — a curator fixing a mis-filed app's
-- country would leave the replacement row pointing at the old one, and the suppression would then
-- apply in a country the local app is not even listed for.
--
-- The cost is one join on a primary key. The benefit is that "which apps are suppressed in CN" has
-- exactly one answer by construction.

CREATE TABLE travel_app_replacement (
    id                 uuid          PRIMARY KEY,

    -- The local app to recommend instead. CASCADE because a replacement with no local alternative
    -- is not a fact this table can express: the row's whole content is "install this one".
    local_app_id       uuid          NOT NULL,

    -- The globally-known app that does not work here. A slug, not a display name, so a curator
    -- cannot create two rows for `Uber` and `uber`.
    replaced_app_key   varchar(120)  NOT NULL,

    -- What to call it on screen. Stored rather than derived from the key: `google-maps` renders as
    -- "Google Maps", and title-casing a slug in the UI produces "Google-Maps" and, eventually,
    -- "Whatsapp".
    replaced_app_name  varchar(200)  NOT NULL,

    -- Why it does not work, as a category. The UI wording differs per reason — "not available in
    -- this country" and "works, but merchants here do not accept it" are different warnings, and
    -- collapsing them into one sentence makes both of them slightly wrong.
    reason             varchar(32)   NOT NULL,

    -- The traveller-facing sentence. Free text because the specific reason matters: "Uber does not
    -- operate in mainland China" is more useful than the category alone, and a curator writing it
    -- is the point at which somebody checks whether it is still true.
    detail             text          NOT NULL,

    source_id          uuid          NOT NULL,
    -- ADR 010 §6, and shorter in spirit than the 180-day app TTL: market availability changes
    -- without notice, and a stale suppression is worse than a stale store link — it actively tells
    -- a traveller not to use an app that now works.
    retrieved_at       timestamptz   NOT NULL,

    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_travel_app_replacement_local_app
        FOREIGN KEY (local_app_id) REFERENCES travel_app (id) ON DELETE CASCADE,
    CONSTRAINT fk_travel_app_replacement_source
        FOREIGN KEY (source_id) REFERENCES knowledge_source (id),

    -- One statement per (local app, replaced app) pair. Two rows saying Didi replaces Uber for
    -- different reasons is not a richer answer, it is a curation conflict that would render as two
    -- contradictory warnings side by side.
    CONSTRAINT uq_travel_app_replacement_pair UNIQUE (local_app_id, replaced_app_key),

    -- Mirrors domain/enums/AppReplacementReason. MigrationContractTest asserts the two lists are
    -- identical, so adding a reason without widening this fails the build rather than the insert —
    -- the same precedent as ck_travel_app_category (V16) and ck_message_role (V19).
    CONSTRAINT ck_travel_app_replacement_reason CHECK (reason IN (
        'NOT_AVAILABLE',
        'NETWORK_BLOCKED',
        'NEEDS_LOCAL_PAYMENT',
        'NOT_THE_LOCAL_STANDARD'
    )),

    -- A slug, lower-case, no spaces. Enforced rather than trusted: the key is a join target, and
    -- `Uber ` silently suppresses nothing while looking correct in a seed file.
    CONSTRAINT ck_travel_app_replacement_key_is_slug
        CHECK (replaced_app_key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),

    CONSTRAINT ck_travel_app_replacement_name_not_blank CHECK (btrim(replaced_app_name) <> ''),
    CONSTRAINT ck_travel_app_replacement_detail_not_blank CHECK (btrim(detail) <> '')
);

-- The suppression query: "for this country's pack, which globally-held apps must I hide, and what
-- do I recommend instead". Reached by joining `travel_app` on local_app_id, so this index serves
-- the lookup from the local app's side.
CREATE INDEX ix_travel_app_replacement_local_app ON travel_app_replacement (local_app_id);

-- The reverse direction, for "is `uber` suppressed anywhere in this itinerary's countries" — the
-- shape task 29 (route and mobility) needs when it annotates a leg.
CREATE INDEX ix_travel_app_replacement_replaced_key ON travel_app_replacement (replaced_app_key);

COMMENT ON TABLE travel_app_replacement IS
    'tasks/17 "suppress inactive global alternatives". Which globally-held app does not work in a country, and which local app to install instead.';
COMMENT ON COLUMN travel_app_replacement.replaced_app_key IS
    'Stable slug for a globally-known app (uber, whatsapp, google-maps). Deliberately NOT a travel_app FK — see the header.';
COMMENT ON COLUMN travel_app_replacement.reason IS
    'Mirrors domain/enums/AppReplacementReason; the UI wording differs per reason.';
