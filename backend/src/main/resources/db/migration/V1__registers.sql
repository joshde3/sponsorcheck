-- Trigram similarity for fuzzy company-name matching (DECISIONS.md D7).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- One row per organisation per register.
-- The import job loads a staging copy, validates it, then swaps it in (D4).
CREATE TABLE register_entry (
    id               BIGSERIAL PRIMARY KEY,
    country          CHAR(2)     NOT NULL,          -- 'GB', 'NL', 'IE'
    legal_name       TEXT        NOT NULL,          -- exactly as the register prints it
    normalised_name  TEXT        NOT NULL,          -- output of the shared normaliser
    town             TEXT,                          -- UK only
    route            TEXT,                          -- UK: e.g. 'Skilled Worker'
    rating           TEXT,                          -- UK: e.g. 'Worker (A rating)'
    source_ref       TEXT,                          -- NL: KvK number
    permits_issued   INTEGER,                       -- IE: permits issued this year
    data_as_of       DATE        NOT NULL           -- date the source published this data
);

-- Exact lookups (tier 1) and fuzzy lookups (tier 3) on the normalised name.
CREATE INDEX register_entry_exact_idx ON register_entry (country, normalised_name);
CREATE INDEX register_entry_trgm_idx  ON register_entry USING GIN (normalised_name gin_trgm_ops);

-- One row per import attempt: what ran, when, and why it failed.
CREATE TABLE import_run (
    id            BIGSERIAL PRIMARY KEY,
    source        TEXT        NOT NULL,             -- 'GB', 'NL', 'IE'
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    status        TEXT        NOT NULL,             -- 'RUNNING', 'SUCCEEDED', 'REJECTED', 'FAILED'
    row_count     INTEGER,
    source_url    TEXT,
    message       TEXT                              -- reason for REJECTED / FAILED
);
