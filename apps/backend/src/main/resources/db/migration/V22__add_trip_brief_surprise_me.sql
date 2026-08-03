-- V22 — persist UC-C1-05's open-destination answer.
--
-- Task 19 already extracts `surprise_me`, but without a column the flag survives only on the
-- immediate extraction result and is lost on the save/read path. Store it on `trip_brief` so
-- UC-C1-05 is represented as `destinations = []` plus `surprise_me = true`.

ALTER TABLE trip_brief
    ADD COLUMN surprise_me boolean NOT NULL DEFAULT false;
