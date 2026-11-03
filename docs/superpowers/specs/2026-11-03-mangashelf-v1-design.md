# MangaShelf v1 — Design

Data: 2026-11-03 · Stato: in revisione

## Obiettivo

MangaShelf è un'app web self-hosted per **me e pochi amici**: ognuno tiene traccia della propria
collezione manga, volume per volume, su un catalogo di serie ed edizioni condiviso. Sostituisce il foglio
Excel attuale (Serie → Edizioni → Volumi, Conclusa).

Il progetto riparte da zero e segue `LuuckA21/project-standards` (guide 01–14 e template). Dove questa spec
non dice nulla, vale lo standard.

**Successo per la v1:** posso inserire One Piece con tutte le sue edizioni, spuntare i volumi che possiedo,
vedere a colpo d'occhio cosa mi manca; un amico fa lo stesso con la sua collezione senza vedere la mia.

### Fuori dalla v1

Import da AniList, import dal foglio Excel (i dati si inseriscono a mano), liste acquisti e prezzi,
stato di lettura, wishlist per singolo volume, registrazione pubblica, email, 2FA, inglese, Sentry,
dati per singolo volume (copertina, data di uscita).

## Modello del dominio

- **Serie**: il "cappello" (es. *One Piece*).
- **Edizione**: qualsiasi pubblicazione sotto la serie: edizione regolare, variant singolo, volume
  giapponese, novel, databook, spin-off. Ogni databook o variant è un'edizione a sé (es. "Edizione blu - Red",
  1 volume). Ha:
  - **tipo**: `REGULAR`, `VARIANT`, `JAPANESE` (in UI: Regolare, Variant, Giapponese);
  - **stato**: `ONGOING`, `COMPLETED` (In corso, Conclusa);
  - **volumi usciti** (`volume_count`, ≥ 1); i volumi sono identificati solo dal numero 1..N;
  - **editore** facoltativo e **copertina** facoltativa.
- **Collezione** (per utente): l'utente aggiunge un'edizione alla propria collezione e spunta i volumi che
  possiede. I volumi non spuntati sono quelli che mancano. Un'edizione in collezione con 0 volumi spuntati
  è un desiderio (es. "Volume 1 - Oro").

Catalogo condiviso: tutti gli utenti aggiungono serie ed edizioni; solo l'admin modifica o elimina.

## Architettura

Monorepo secondo la guida 04: `backend/` (Java 25, Spring Boot 4, Maven) e `frontend/` (React + TypeScript
strict + Vite). Un'unica immagine Docker: il frontend compilato viene servito da Spring Boot dalla stessa
origine, con fallback SPA (guida 11). PostgreSQL 17, schema solo tramite Flyway.

Package backend per funzionalità sotto `me.luucka.mangashelf`:

| Package | Responsabilità |
|---|---|
| `config` | `SecurityConfig`, `AppProperties`, fallback SPA |
| `users` | login, sessioni, password, gestione utenti admin, export ed eliminazione account, admin iniziale |
| `catalog` | serie, edizioni, copertine |
| `collection` | edizioni in collezione e volumi posseduti |
| `common` | `@RestControllerAdvice` con Problem Details, eccezioni `NotFound`/`Conflict` |

Controller → Service → Repository; controller sottili, regole e permessi nei service; DTO `record` al bordo.

## Dati

Convenzioni della guida 08: tabelle plurali snake_case, `id uuid` v7 generato dall'app, `created_at` e
`updated_at` `timestamptz` in UTC, vincoli con nome, indice su ogni FK, `ON DELETE` esplicito, enum come
`text` + `CHECK`, migrazioni `V<timestamp>__<descrizione>.sql`, `ddl-auto: validate`, `open-in-view: false`.

| Tabella | Colonne (oltre a id, created_at, updated_at) | Vincoli |
|---|---|---|
| `users` | `username text`, `password_hash text`, `role text`, `must_change_password boolean` | `uq_users_username` su `lower(username)`; `ck_users_role` (`ADMIN`, `USER`); username 3–32 caratteri `[a-zA-Z0-9._-]` |
| `series` | `title text`, `created_by uuid null` | `uq_series_title` su `lower(title)`; FK `created_by` → users `ON DELETE SET NULL` |
| `editions` | `series_id uuid`, `name text`, `publisher text null`, `type text`, `status text`, `volume_count int`, `cover_file text null`, `created_by uuid null`, `version bigint` | `uq_editions_series_id_name` su `(series_id, lower(name))`; `ck_editions_type`; `ck_editions_status`; `ck_editions_volume_count` (≥ 1, ≤ 9999); FK `series_id` `ON DELETE RESTRICT`; FK `created_by` `ON DELETE SET NULL` |
| `collection_editions` | `user_id uuid`, `edition_id uuid` | `uq_collection_editions_user_id_edition_id`; FK user `ON DELETE CASCADE`; FK edition `ON DELETE CASCADE` |
| `owned_volumes` | `collection_edition_id uuid`, `number int` | `uq_owned_volumes_collection_edition_id_number`; `ck_owned_volumes_number` (≥ 1); FK `ON DELETE CASCADE` |
| Spring Session | `spring_session`, `spring_session_attributes` | schema ufficiale di `spring-session-jdbc`, creato da una migrazione Flyway |

Nullable con motivo: `publisher` e `cover_file` sono facoltativi; `created_by` resta null quando l'autore
viene eliminato, così il catalogo sopravvive.

Regole:

- I volumi mancanti non sono salvati: sono `1..volume_count` meno `owned_volumes`.
- Abbassare `volume_count` sotto un numero posseduto da qualcuno → 409.
- `editions.version` con `@Version`: due modifiche concorrenti → 409.
- Eliminare un'edizione la toglie dalla collezione di tutti (cascade); la UI mostra prima in quante
  collezioni è presente.
- Una serie si elimina solo se non ha edizioni (409 altrimenti).
- Eliminare un utente cancella la sua collezione, non il catalogo.

## API

Guida 07: tutto sotto `/api/v1`, Problem Details (`application/problem+json`, lista `errors` per la
validazione), DTO `record` validati, ID come stringhe, liste paginate (`max-page-size: 100`, serializzazione
`VIA_DTO`), 401 senza login, 403 su endpoint admin, 404 per ciò che non esiste o non è visibile.
springdoc attivo in dev e staging, spento in prod.

### Auth e account

| Endpoint | Chi | Note |
|---|---|---|
| `POST /auth/login` | pubblico | `{username, password}`; errore unico "Credenziali non valide" |
| `POST /auth/logout` | loggato | 204 |
| `GET /auth/me` | loggato | `{id, username, role, mustChangePassword}` |
| `POST /auth/password` | loggato | `{currentPassword, newPassword}`; azzera `must_change_password`; chiude tutte le sessioni dell'utente, compresa la corrente |
| `GET /account/export` | loggato | JSON con profilo (username, ruolo, data creazione) e collezione |
| `DELETE /account` | loggato | `{password}`; 409 se è l'ultimo admin |

Con `must_change_password` attivo, tutte le API tranne `GET /auth/me`, `POST /auth/password` e
`POST /auth/logout` rispondono 403.

### Utenti (solo admin)

| Endpoint | Note |
|---|---|
| `GET /users` | paginato |
| `POST /users` | `{username, temporaryPassword, role}` → 201; `must_change_password = true`; 409 se lo username esiste |
| `POST /users/{id}/reset-password` | `{temporaryPassword}`; chiude le sessioni dell'utente |
| `PATCH /users/{id}` | `{role}`; chiude le sessioni dell'utente; 409 sul proprio ruolo o se toglie l'ultimo admin |
| `DELETE /users/{id}` | 204; 409 su se stessi (si usa `DELETE /account`) o sull'ultimo admin |

### Catalogo

| Endpoint | Chi | Note |
|---|---|---|
| `GET /series?q=` | loggato | paginato, ordinato per titolo; per serie il numero di edizioni |
| `GET /series/{id}` | loggato | serie con tutte le edizioni; per ogni edizione se è nella mia collezione |
| `POST /series` | loggato | `{title}` → 201; 409 se il titolo esiste |
| `PUT /series/{id}` | admin | `{title}` |
| `DELETE /series/{id}` | admin | 409 se ha edizioni |
| `POST /series/{id}/editions` | loggato | `{name, publisher?, type, status, volumeCount}` → 201 |
| `GET /editions/{id}` | loggato | include `collectionCount` (in quante collezioni) e `version` |
| `PUT /editions/{id}` | admin | stesso corpo + `version`; 409 su versione vecchia o `volumeCount` troppo basso |
| `DELETE /editions/{id}` | admin | 204 |
| `PUT /editions/{id}/cover` | autore dell'edizione o admin | multipart, campo `file`, max 5 MB |
| `DELETE /editions/{id}/cover` | autore dell'edizione o admin | 204 |
| `GET /editions/{id}/cover` | loggato | `image/jpeg`, `nosniff`, `Cache-Control: private` |

### Collezione (sempre dell'utente in sessione)

| Endpoint | Note |
|---|---|
| `GET /collection?q=&type=&status=&incomplete=` | paginato, ordinato per titolo della serie e nome dell'edizione; per edizione: serie, dati edizione, `owned` (lista numeri), `volumeCount` |
| `PUT /collection/editions/{editionId}` | aggiunge (idempotente) → 200 |
| `DELETE /collection/editions/{editionId}` | toglie, con i volumi spuntati → 204 |
| `PUT /collection/editions/{editionId}/volumes` | `{owned: [1,2,5]}` sostituisce l'insieme; 400 per numeri fuori da 1..`volumeCount` o duplicati; aggiunge l'edizione alla collezione se non c'è |

Due schede aperte sulla stessa edizione: vince l'ultima scrittura. Sono dati di un solo utente, accettato.

## Frontend

Guida 11 e `docs/frontend-style.md`.

- Struttura per funzionalità: `src/features/{auth,collection,catalog,users,account}`, componenti condivisi
  in `src/components/`, client in `src/api/client.ts` (cookie same-origin, `X-XSRF-TOKEN`, `ApiError` da
  Problem Details, 401 → `/login`).
- React Router, TanStack Query per i dati del server, route caricate con `React.lazy`.
- Testi solo in italiano, ma tutti tramite `t('chiave')` da un unico `src/i18n/it.ts`; le chiavi sono
  tipizzate (`keyof typeof it`). Nessuna libreria i18n nella v1.

### Pagine

| Percorso | Contenuto |
|---|---|
| `/login` | accesso; se la password è temporanea porta a `/change-password` |
| `/change-password` | cambio password obbligatorio |
| `/` | la mia collezione: edizioni raggruppate per serie, tipo, stato, "posseduti X/Y"; filtri testo, tipo, stato, "solo incomplete" (chip) |
| `/editions/:id` | copertina, dati, scaffale dei volumi 1..N, "Segna tutti" / "Nessuno", riepilogo "Mancano: 3, 7–9", aggiungi/togli dalla collezione; admin: modifica, elimina |
| `/catalog` | ricerca serie, crea serie |
| `/series/:id` | edizioni della serie, "Aggiungi alla mia collezione", crea edizione (con copertina); admin: modifica, elimina |
| `/admin/users` | solo admin: crea utente, reimposta password, cambia ruolo, elimina |
| `/account` | cambia password, esporta dati, elimina account |
| `/privacy` | informativa privacy, linkata nel footer |

### Scaffale

Ogni clic su una tile aggiorna subito la UI (ottimistico) e invia l'insieme completo dei posseduti; se la
chiamata fallisce la UI torna allo stato precedente e mostra `.error`. Le tile sono `<button>` con
`aria-pressed` e etichetta "Volume 12, posseduto" / "Volume 12, mancante".

### Stile

`docs/frontend-style.md` è la copia della guida di stile fornita, adattata alla v1:

- valgono principio, token, tipografia, spaziature, componenti, responsive, accessibilità e convenzioni CSS;
- scaffale: solo `.tile` (posseduto), `.tile.missing` (mancante), `.tile.busy` (salvataggio); niente
  `.tile.future`, perché `volume_count` è sempre noto;
- eliminazioni con un componente `ConfirmDelete` inline, niente `window.confirm` né dialoghi;
- via le parti degli acquisti (`.reserved`, `.bought`, totali, statistiche) e l'eccezione 2FA;
- i18n: solo italiano tramite `t()` (sostituisce la regola italiano + inglese);
- i link a componenti del vecchio progetto diventano i nomi dei nuovi componenti.

## Account e sicurezza

Guida 03; impostazioni di Spring Security attive, CSRF attivo (pattern SPA con cookie `XSRF-TOKEN`).

- **Admin iniziale**: all'avvio, se `users` è vuota e `ADMIN_USERNAME` e `ADMIN_INITIAL_PASSWORD` sono
  impostati, viene creato un admin con `must_change_password = true`. Con utenti già presenti le variabili
  sono ignorate. Dopo il primo accesso la password va tolta da `.env`.
- **Password**: `DelegatingPasswordEncoder` (bcrypt); lunghezza 12–64 caratteri, validata su ogni DTO che
  la riceve.
- **Sessioni**: Spring Session JDBC, scadenza dopo 14 giorni di inattività; cookie `HttpOnly`,
  `SameSite=Lax`, `Secure` fuori da dev. Cambio password, reset, cambio ruolo ed eliminazione chiudono tutte
  le sessioni dell'utente interessato (`FindByIndexNameSessionRepository`).
- **Login**: messaggio unico per username o password sbagliati; rate limit con `limit_req` in Nginx Proxy
  Manager su `/api/v1/auth/login` (`http_top.conf` degli standard).
- **Permessi**: verificati nei service. Collezione: l'utente arriva sempre dalla sessione, mai dall'URL.
- **Copertine**: accettati solo JPEG e PNG, riconosciuti dal contenuto (lettura con `ImageIO`), max 5 MB;
  ricodificati in JPEG entro 480×720 mantenendo le proporzioni (così spariscono EXIF e posizione GPS);
  salvati come `<uuid>.jpg` nel volume `uploads` (`/app/uploads`). Il file precedente viene cancellato
  dopo la sostituzione o l'eliminazione dell'edizione.
- **Header**: CSP `default-src 'self'; img-src 'self' blob:`, `nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: strict-origin-when-cross-origin`; HSTS dal proxy.
- **Actuator**: solo `health`.
- **Privacy** (guida 09): `docs/privacy.md` dal template e pagina `/privacy`. Dati personali: username,
  hash password, collezione, dati di sessione, IP nei log del proxy (14–30 giorni). Fornitori: Hetzner
  (hosting), Swiss Backup (backup). Nessun cookie di tracciamento, quindi nessun banner.

## Errori

- Un `@RestControllerAdvice` mappa `NotFoundException` → 404, `ConflictException` → 409,
  `ObjectOptimisticLockingFailureException` → 409, violazioni di vincoli unici → 409, validazione → 400 con
  `errors[]`, `AccessDeniedException` → 403. Mai stack trace, SQL o nomi di classe in `detail`.
- Il frontend mostra gli errori di validazione accanto al campo (`errors[].field`) e gli altri con `.error`.

## Test

- **Backend**: JUnit 5 + AssertJ; integrazione con `@SpringBootTest` e Testcontainers `postgres:17`.
  Priorità:
  - auth e permessi: 401 senza login, 403 endpoint admin, blocco con password temporanea, ultimo admin,
    sessioni chiuse dopo cambio password e reset, isolamento delle collezioni tra utenti;
  - scritture: volumi fuori range (400), `volumeCount` sotto un posseduto (409), versione vecchia (409),
    serie con edizioni (409), cascade su eliminazione edizione e utente;
  - copertine: rifiuto di file non immagine, JPEG risultante senza EXIF e ≤ 480×720.
- **Frontend**: Vitest + Testing Library + `vitest-axe`: client API (CSRF, Problem Details, 401), scaffale
  (toggle, rollback su errore, `aria-pressed`), `styles.test.ts` (contrasto dei token), axe sulle pagine
  principali.
- **Qualità**: Spotless (google-java-format), `-Xlint:all`, ESLint + `jsx-a11y`, Prettier (`semi: false`,
  `singleQuote: true`), `tsc`. Tutto in `./mvnw verify` e negli script `lint`, `typecheck`, `test`, `build`.

## Sviluppo locale

Guida 10: `backend/compose.yaml` avviato da Spring Boot, profilo `dev` con dati di prova in
`db/dev-data/` (un admin, un utente, One Piece con alcune edizioni del foglio Excel), Vite su `:5173` con
proxy `/api` → `:8080`.

## Repository, CI/CD, deploy e backup

- Avvio con `new-project.sh --spring` di project-standards (template, `CLAUDE.md`, `.editorconfig`,
  `.github/`, `.nvmrc`, `.sdkmanrc`). La parte `--github` (impostazioni e ruleset su `main`) la esegue il
  proprietario del repo.
- Versione iniziale `0.1.0-SNAPSHOT`; Conventional Commits, PR squash su `main` (guida 02).
- CI/CD dai template: `ci.yml`, `release.yml`, `deploy.yml`; immagine `ghcr.io/luucka21/mangashelf`;
  Dependabot per maven, npm e actions.
- Deploy su `prd-apps-01` in `/srv/apps/mangashelf`, porta 8082 (prod) e 8083 (staging); va aggiunta la
  riga in `apps/README.md` di project-standards.
- Backup con il `backup.sh` del template: PostgreSQL e copertine (`BACKUP_UPLOADS_DIR=/app/uploads`) su
  restic / Swiss Backup; primo test di ripristino prima di considerarlo valido.
- Monitoraggio: log strutturati ECS con rotazione, uptime check su `/actuator/health`. Niente Sentry nella v1.
