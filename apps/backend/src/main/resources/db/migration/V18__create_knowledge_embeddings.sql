-- V18 — embeddings and the vector indexes (ADR 010 §5).
--
-- Three rules from the ADR are enforced here by the SCHEMA rather than by convention, because each
-- of them fails silently when it fails at all.
--
-- 1. ONE MODEL PER INDEX. Vectors from two models are not comparable; mixing them corrupts
--    retrieval without any error. `vector(1536)` makes a wrong-dimension write impossible at the
--    type level, a CHECK pins the model name, and every HNSW index below is PARTIAL on that model
--    name — so a future model physically cannot share an index with this one.
--
-- 2. PRE-ANN DESTINATION FILTERING. Post-filtering an HNSW result destroys recall: the index
--    returns its global top-k and the filter then throws most of it away, so a destination with
--    few rows can come back empty. Postgres only filters before the scan when the predicate
--    matches a PARTIAL index, so there is one index per destination. `destination_slug` is
--    denormalised onto both tables for exactly this reason — a partial index predicate has to be
--    a constant, and a uuid generated at seed time is not knowable here, while ADR 010 §1 fixes
--    the three v1 slugs.
--
-- 3. PER-FIELD-GROUP CHUNKING. A guide row produces three vectors, not one. `destination_guide`
--    is heterogeneous — `overview`, `food` and `practical` answer different questions — and
--    embedding it whole produces the unusable centroid ADR 010 rejects.
--
-- Adding a fourth destination therefore needs a migration, which ADR 010 already anticipates:
-- "Adding a destination is a documented, repeatable authoring procedure."

-- Pinned by ADR 010 §5. Repeated in CHECK constraints below rather than referenced, because a
-- Postgres CHECK cannot call a function that might later change its answer.
--   model     text-embedding-3-small
--   dimension 1536

CREATE TABLE destination_guide_embedding (
    id                  uuid          PRIMARY KEY,
    guide_id            uuid          NOT NULL,
    -- Denormalised from destination_guide -> destination. Carried so the partial indexes below can
    -- filter before the ANN scan; kept in step by the ingest path, which writes both together.
    destination_id      uuid          NOT NULL,
    destination_slug    varchar(120)  NOT NULL,
    -- Which part of the guide this vector represents (ADR 010 §5 chunking).
    field_group         varchar(32)   NOT NULL,
    embedding           vector(1536)  NOT NULL,
    embedding_model     varchar(120)  NOT NULL,
    embedding_dimension integer       NOT NULL,
    -- Bumped when the CHUNKING or prompt changes while the model does not, so a re-embed can be
    -- forced without pretending the model moved.
    embedding_version   integer       NOT NULL DEFAULT 1,
    -- ADR 010 §5: the re-embed trigger. Ingest compares this against the hash of the source text
    -- and skips unchanged rows, which is what stops a nightly refresh from re-embedding the whole
    -- corpus and paying for it.
    content_hash        char(64)      NOT NULL,
    embedded_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_guide_embedding_guide FOREIGN KEY (guide_id)
        REFERENCES destination_guide (id) ON DELETE CASCADE,
    CONSTRAINT fk_guide_embedding_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    -- One vector per guide per field group per model. The model is part of the key so an additive
    -- migration (ADR 010 §5) can hold both models' vectors at once while the new index builds.
    CONSTRAINT uq_guide_embedding_group_model
        UNIQUE (guide_id, field_group, embedding_model),
    CONSTRAINT ck_guide_embedding_field_group CHECK (
        field_group IN ('OVERVIEW', 'FOOD', 'PRACTICAL')
    ),
    -- Belt and braces alongside `vector(1536)`: the type rejects a wrong-length vector, and this
    -- rejects a row that MISDECLARES its own dimension, which is what a buggy writer produces.
    CONSTRAINT ck_guide_embedding_dimension CHECK (embedding_dimension = 1536),
    CONSTRAINT ck_guide_embedding_model CHECK (embedding_model = 'text-embedding-3-small'),
    CONSTRAINT ck_guide_embedding_version_positive CHECK (embedding_version >= 1)
);

CREATE INDEX ix_guide_embedding_destination ON destination_guide_embedding (destination_id);
CREATE INDEX ix_guide_embedding_content_hash ON destination_guide_embedding (content_hash);

CREATE TABLE poi_embedding (
    id                  uuid          PRIMARY KEY,
    poi_id              uuid          NOT NULL,
    destination_id      uuid          NOT NULL,
    destination_slug    varchar(120)  NOT NULL,
    -- One chunk per POI: ADR 010 §5 embeds `name + description + tags` together, because a POI is
    -- short enough that splitting it would produce fragments with no standalone meaning.
    embedding           vector(1536)  NOT NULL,
    embedding_model     varchar(120)  NOT NULL,
    embedding_dimension integer       NOT NULL,
    embedding_version   integer       NOT NULL DEFAULT 1,
    content_hash        char(64)      NOT NULL,
    embedded_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_poi_embedding_poi FOREIGN KEY (poi_id) REFERENCES poi (id) ON DELETE CASCADE,
    CONSTRAINT fk_poi_embedding_destination FOREIGN KEY (destination_id)
        REFERENCES destination (id) ON DELETE CASCADE,
    CONSTRAINT uq_poi_embedding_poi_model UNIQUE (poi_id, embedding_model),
    CONSTRAINT ck_poi_embedding_dimension CHECK (embedding_dimension = 1536),
    CONSTRAINT ck_poi_embedding_model CHECK (embedding_model = 'text-embedding-3-small'),
    CONSTRAINT ck_poi_embedding_version_positive CHECK (embedding_version >= 1)
);

CREATE INDEX ix_poi_embedding_destination ON poi_embedding (destination_id);
CREATE INDEX ix_poi_embedding_content_hash ON poi_embedding (content_hash);

-- ---------------------------------------------------------------------------------------------
-- HNSW indexes — vector_cosine_ops, one per (destination, table).
--
-- IVFFlat is rejected by ADR 010: it needs a populated table to build meaningful lists, and at
-- this data size HNSW simply recalls better.
--
-- m / ef_construction are left at pgvector's defaults (16 / 64). With three destinations and a few
-- hundred rows each, tuning them would be guessing against a corpus that does not exist yet; task
-- 17 seeds the data and task 40 owns re-embedding, either of which is a better place to measure.
--
-- Every predicate names BOTH the destination and the model. The model half is what makes rule 1
-- structural: a second model's vectors cannot enter these indexes even by accident.
-- ---------------------------------------------------------------------------------------------

CREATE INDEX ix_guide_embedding_hnsw_tokyo ON destination_guide_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE destination_slug = 'tokyo-jp' AND embedding_model = 'text-embedding-3-small';

CREATE INDEX ix_guide_embedding_hnsw_bangkok ON destination_guide_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE destination_slug = 'bangkok-th' AND embedding_model = 'text-embedding-3-small';

CREATE INDEX ix_guide_embedding_hnsw_shanghai ON destination_guide_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE destination_slug = 'shanghai-cn' AND embedding_model = 'text-embedding-3-small';

CREATE INDEX ix_poi_embedding_hnsw_tokyo ON poi_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE destination_slug = 'tokyo-jp' AND embedding_model = 'text-embedding-3-small';

CREATE INDEX ix_poi_embedding_hnsw_bangkok ON poi_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE destination_slug = 'bangkok-th' AND embedding_model = 'text-embedding-3-small';

CREATE INDEX ix_poi_embedding_hnsw_shanghai ON poi_embedding
    USING hnsw (embedding vector_cosine_ops)
    WHERE destination_slug = 'shanghai-cn' AND embedding_model = 'text-embedding-3-small';
