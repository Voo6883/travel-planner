-- V1 — extension baseline.
--
-- Extensions are their own migration because they are the one schema object that can fail for a
-- reason unrelated to the application: the image must actually ship them. Isolating them means a
-- database started from plain `postgres:16` instead of `pgvector/pgvector:pg16` fails here, with
-- an unambiguous message, rather than three migrations later on a CREATE INDEX.
--
-- `vector` is enabled now even though no embedding column exists yet (those belong to
-- tasks/16 and tasks/20). Enabling an extension requires superuser; doing it in the baseline
-- keeps every later knowledge migration runnable by the ordinary application role.

CREATE EXTENSION IF NOT EXISTS vector;
