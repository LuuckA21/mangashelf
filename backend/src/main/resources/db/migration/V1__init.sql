-- ============================================================
-- MangaShelf - schema iniziale
--
--   manga   = l'opera (One Piece)
--   series  = una edizione pubblicata (Normale, New Edition, Gazzetta)
--   volume  = il singolo volume di una edizione
--   user_volume = i volumi posseduti da un utente
--
-- Voto, wishlist, tag e dettagli d'acquisto sono volutamente assenti:
-- si aggiungono con una migrazione successiva quando serviranno.
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
-- Catalogo, condiviso fra tutti gli utenti
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
    total_volumes  SMALLINT,             -- volumi dell'edizione originale JP
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
    total_volumes  SMALLINT,                -- NULL se in corso e ignoto
    completed      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (manga_id, publisher, language, name)
);

CREATE INDEX idx_series_manga ON series (manga_id);

CREATE TABLE volume (
    id             BIGSERIAL PRIMARY KEY,
    series_id      BIGINT NOT NULL REFERENCES series(id) ON DELETE CASCADE,
    number         SMALLINT NOT NULL,
    title          VARCHAR(300),
    isbn13         VARCHAR(13),
    release_date   DATE,
    cover_url      VARCHAR(1000),
    UNIQUE (series_id, number)
);

CREATE INDEX idx_volume_series ON volume (series_id);
CREATE INDEX idx_volume_isbn ON volume (isbn13);

-- ------------------------------------------------------------
-- Collezione personale
-- ------------------------------------------------------------

-- Quali volumi possiede un utente. La chiave primaria e' la coppia
-- stessa: un utente possiede un volume oppure no, non esiste un terzo
-- stato, quindi non serve una chiave surrogata.
CREATE TABLE user_volume (
    user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    volume_id  BIGINT NOT NULL REFERENCES volume(id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, volume_id)
);

CREATE INDEX idx_user_volume_volume ON user_volume (volume_id);
