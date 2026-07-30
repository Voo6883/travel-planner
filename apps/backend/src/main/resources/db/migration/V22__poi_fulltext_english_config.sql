-- V22 — the POI full-text index cannot match a plural against a singular.
--
-- V15 created ix_poi_fulltext with the `simple` text-search configuration. `simple` lower-cases and
-- splits on non-word characters and does nothing else: no stemming, no stopword list. So a query for
-- `temples` does not match a POI named "Sample Old Town Temple", and `markets` does not match
-- "Sample Craft Market". Measured on PostgreSQL 16.6:
--
--   to_tsvector('simple' ,'Sample Old Town Temple') @@ websearch_to_tsquery('simple' ,'temples') -> false
--   to_tsvector('english','Sample Old Town Temple') @@ websearch_to_tsquery('english','temples') -> true
--
-- `temples` is not a hypothetical query. ADR 010 §5 and task 17's Definition of Done both name
-- "street food" and "temples" as the two examples hybrid retrieval exists to serve, on the grounds
-- that they are lexical and underperform under pure vector search. With `simple`, the lexical arm
-- answers one of the two.
--
-- Why this was invisible: the sample seed's POI *description* happens to contain the plural form as
-- well as the singular in its name, so a test asserting "the temples query finds the temple POI"
-- passes on this dataset while the mechanism is broken. Real curation writing "Senso-ji Temple" with
-- no plural anywhere would have silently returned nothing, and pure vector would have covered for it
-- well enough that nobody looked.
--
-- `english` on non-English content: a token no dictionary recognises is passed through unchanged, so
-- Thai, Chinese and Malay text behaves as it did under `simple`. There is nothing to lose. ADR 010 §5
-- also makes `en` the authoritative locale for embedded text, so English stemming matches the
-- language the indexed columns are actually written in.
--
-- The trade `english` does make is stopwords: a POI named "The Bund" indexes as `bund` alone, so a
-- query of literally "the" matches nothing. That is the correct behaviour for a search box and the
-- reason no full-text configuration keeps them.
--
-- Expression index, not a stored tsvector column — V15's reasoning, unchanged: no second copy of the
-- text to keep in step. `to_tsvector(regconfig, text)` is IMMUTABLE and therefore indexable, while
-- the single-argument form reads a GUC, is only STABLE, and Postgres refuses it in an index. Naming
-- the configuration is required, not stylistic.

DROP INDEX ix_poi_fulltext;

CREATE INDEX ix_poi_fulltext ON poi
    USING gin (to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, '')));

COMMENT ON INDEX ix_poi_fulltext IS
    'Lexical arm of ADR 010 §5 hybrid retrieval. The `english` configuration is load-bearing: '
    'KnowledgeFullTextSearch must name the same one, or the query cannot use this index and '
    'recall degrades to whatever the vector arm alone finds — silently.';
