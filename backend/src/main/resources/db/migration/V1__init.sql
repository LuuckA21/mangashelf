-- ============================================================
-- MangaShelf - initial schema
--
--   manga   = the work itself (One Piece)
--   series  = one published edition (Normale, New Edition, Gazzetta)
--   user_volume = the volumes a user owns, by number
--
-- There is deliberately no table of volumes. Cataloguing which volumes an
-- edition contains would mean knowing how many the publisher has released,
-- which nobody here does — and a gap inside a run is visible from the owned
-- numbers alone. What lies beyond the highest owned number is the business
-- of the purchase lists, not of a catalogue.
-- ============================================================

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(72)  NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ------------------------------------------------------------
-- Catalogue, shared by every user
-- ------------------------------------------------------------

CREATE TABLE manga (
    id             BIGSERIAL PRIMARY KEY,
    anilist_id     INTEGER UNIQUE,
    mal_id         INTEGER UNIQUE,
    title_romaji   VARCHAR(500) NOT NULL,
    title_native   VARCHAR(500),
    title_english  VARCHAR(500),
    authors        TEXT,
    description    TEXT,
    cover_url      VARCHAR(1000),
    status         VARCHAR(32),          -- FINISHED, RELEASING, HIATUS, ...
    genres         TEXT[],
    start_year     SMALLINT,
    total_volumes  SMALLINT,             -- volumes of the original Japanese run
    synced_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_manga_title_romaji ON manga (lower(title_romaji));

CREATE TABLE series (
    id             BIGSERIAL PRIMARY KEY,
    manga_id       BIGINT NOT NULL REFERENCES manga(id) ON DELETE CASCADE,
    publisher      VARCHAR(200) NOT NULL,   -- Star Comics, Planet Manga, ...
    language       VARCHAR(2)   NOT NULL DEFAULT 'it',
    name           VARCHAR(300) NOT NULL,   -- "Normale", "New Edition", "Gazzetta"

    -- Optional, and usually unknown: when it is set the shelf knows how far
    -- the run goes, when it is not the shelf stops at the highest volume
    -- owned.
    total_volumes  SMALLINT,
    completed      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (manga_id, publisher, language, name)
);

CREATE INDEX idx_series_manga ON series (manga_id);

-- ------------------------------------------------------------
-- Personal collection
-- ------------------------------------------------------------

-- Which volume numbers a user owns of an edition. The triple is the whole
-- fact: a user either owns volume 47 of this run or does not, and there is
-- no third state a surrogate key could distinguish.
CREATE TABLE user_volume (
    user_id    BIGINT   NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    series_id  BIGINT   NOT NULL REFERENCES series(id) ON DELETE CASCADE,
    number     SMALLINT NOT NULL CHECK (number >= 0),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, series_id, number)
);

CREATE INDEX idx_user_volume_series ON user_volume (series_id);
