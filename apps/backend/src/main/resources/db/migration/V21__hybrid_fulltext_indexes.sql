-- V21 — hybrid retrieval full-text indexes (ADR 010 §5, task 17).
--
-- V15 already created ix_poi_fulltext over name + description. ADR 010 §5 embeds
-- `name + description + tags` as one chunk, so lexical search must see the same surface or a query
-- like "street food" that only hits a tag would be invisible to the tsvector half of fusion.
-- destination_guide had no GIN index at all; hybrid guide search would always seq-scan.
--
-- Expression indexes (not stored tsvector columns) keep a single copy of the text, matching V15.

DROP INDEX IF EXISTS ix_poi_fulltext;

CREATE INDEX ix_poi_fulltext ON poi
    USING gin (to_tsvector(
        'simple',
        coalesce(name, '') || ' ' || coalesce(description, '') || ' '
            || coalesce(array_to_string(tags, ' '), '')
    ));

CREATE INDEX ix_destination_guide_fulltext ON destination_guide
    USING gin (to_tsvector(
        'simple',
        coalesce(overview, '') || ' ' || coalesce(food, '') || ' ' || coalesce(practical, '')
    ));
