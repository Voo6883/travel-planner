-- V21 — hybrid retrieval full-text indexes (ADR 010 §5, task 17).
--
-- V15 already created ix_poi_fulltext over name + description. ADR 010 §5 embeds
-- `name + description + tags` as one chunk, so lexical search must see the same surface or a query
-- like "street food" that only hits a tag would be invisible to the tsvector half of fusion.
-- destination_guide had no GIN index at all; hybrid guide search would always seq-scan.
--
-- Expression indexes (not stored tsvector columns) keep a single copy of the text, matching V15.
--
-- `array_to_string` is STABLE in Postgres, so it cannot appear in an expression index (42P17).
-- Wrap it in an IMMUTABLE SQL function so the GIN predicate stays indexable; the search SQL in
-- KnowledgeVectorSearch must call the same function or the planner cannot match this index.

CREATE OR REPLACE FUNCTION knowledge_tags_text(tags text[])
RETURNS text
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$ SELECT array_to_string(tags, ' ') $$;

DROP INDEX IF EXISTS ix_poi_fulltext;

CREATE INDEX ix_poi_fulltext ON poi
    USING gin (to_tsvector(
        'simple',
        coalesce(name, '') || ' ' || coalesce(description, '') || ' '
            || coalesce(knowledge_tags_text(tags), '')
    ));

CREATE INDEX ix_destination_guide_fulltext ON destination_guide
    USING gin (to_tsvector(
        'simple',
        coalesce(overview, '') || ' ' || coalesce(food, '') || ' ' || coalesce(practical, '')
    ));
