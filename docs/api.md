# MangaShelf API

A guide for clients other than the web app (for example a mobile app). The full machine-readable
description is [`openapi.json`](openapi.json) (OpenAPI 3, generated from the code; a test fails when it is out
of date). Outside production the server also serves it at `/v3/api-docs`, and Swagger UI at
`/swagger-ui.html`.

## Basics

- Base path: `/api/v1` on the app's domain, HTTPS only (e.g. `https://manga.luucka.me/api/v1`).
- JSON in and out (`Content-Type: application/json`), except the cover upload (multipart) and the cover image.
- Ids are UUID strings. Dates are ISO `YYYY-MM-DD`; timestamps ISO 8601 in UTC.
- **Money is integer cents** (`priceChfCents: 790` = CHF 7.90). Never send decimals.
- Enums: edition `type` = `REGULAR` | `VARIANT` | `JAPANESE`; `status` = `ONGOING` | `COMPLETED`;
  user `role` = `ADMIN` | `USER`.
- Paged lists take `page` (from 0), `size` and `sort` (e.g. `sort=title,asc`) and answer
  `{ "content": [...], "page": { "number", "size", "totalElements", "totalPages" } }`.
- Created resources answer **201** (with a `Location` header, except purchase rows); deletes answer **204**
  (except purchase rows, which return the updated list).

## Authentication

The API uses a **server session in a cookie** plus a **CSRF token**, the same as the web app. There are no
bearer tokens.

| Cookie | What it is |
|---|---|
| `SESSION` | The login session. HttpOnly, Secure, SameSite=Lax. Lasts 14 days **of inactivity**: every API call with a valid session sends it again with a fresh expiry. |
| `XSRF-TOKEN` | The CSRF token, readable by the client. Sent on every response. Changes at login and logout. |

Rules:

1. Keep both cookies like a browser would (a cookie jar: `CookieJar` on Android/OkHttp, `HTTPCookieStorage`
   with `URLSession` on iOS) and send them back on every request.
2. On every request that is **not** `GET` (`POST`, `PUT`, `PATCH`, `DELETE`), send the current value of the
   `XSRF-TOKEN` cookie in the header **`X-XSRF-TOKEN`** (URL-decoded). Without it the server answers 403.
3. Before the first write, if you have no `XSRF-TOKEN` yet, call `GET /api/v1/auth/me` once: even when it answers
   401, the response sets the cookie.
4. After login and logout, read the token again from the cookie: it has changed.

Login flow:

```http
GET /api/v1/auth/me                      → 401, sets XSRF-TOKEN

POST /api/v1/auth/login
X-XSRF-TOKEN: <token>
{ "username": "anna", "password": "…" }
                                          → 200 { "id": "…", "username": "anna", "role": "USER",
                                                  "mustChangePassword": false }
                                            sets SESSION and a new XSRF-TOKEN
```

- **Wrong credentials** → 401. Too many login attempts → 429 (rate limit at the proxy).
- **`mustChangePassword: true`** (new account or password reset by an admin): every API call answers **403**
  except `GET /auth/me`, `POST /auth/login`, `POST /auth/password` and `POST /auth/logout`. Change the password
  first; the change ends all the user's sessions, so log in again afterwards.
- **Session expired or ended** → 401 on any call: show the login.

Known limit: a cookie session works in native apps but is less convenient than tokens. If a mobile app needs
something else (for example long-lived tokens), it is a backend change to design first.

## Errors

Errors are [Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):

```json
{ "status": 400, "title": "Bad Request", "detail": "Dati non validi",
  "errors": [ { "field": "name", "message": "Editore già presente" } ] }
```

| Status | Meaning |
|---|---|
| 400 | Invalid input. `errors[]` lists the fields (`field`, `message`); messages are Italian, ready to show. |
| 401 | Not logged in, or the session expired. |
| 403 | Not allowed: admin-only action, missing `X-XSRF-TOKEN`, or the password must change. |
| 404 | Not found — also for another user's private resources (purchase lists). |
| 409 | Conflict: the resource is in use (e.g. a publisher used by editions), a duplicate, the last admin, or an edition changed by someone else meanwhile. `detail` explains. |
| 413 | Upload too large (cover over 5 MB). |
| 429 | Too many login attempts. |

## Endpoints

"Who": **user** = any logged-in user; **admin** = role `ADMIN` only (others get 403); **owner** = only for
the user's own data. Bodies list their fields; `*` = required. Full schemas: [`openapi.json`](openapi.json).

### Session and password

| Method | Path | Who | Body → Response |
|---|---|---|---|
| GET | `/auth/me` | user | → `MeResponse { id, username, role, mustChangePassword }` |
| POST | `/auth/login` | everyone | `{ username*, password* }` → `MeResponse` |
| POST | `/auth/logout` | user | → 204 |
| POST | `/auth/password` | user | `{ currentPassword*, newPassword* }` → 204, ends all sessions |

### Catalog: series and editions

The catalog is shared by all users. Any user can create series and editions; renaming or deleting them is for
admins.

| Method | Path | Who | Body → Response |
|---|---|---|---|
| GET | `/series?q=&page=&size=&sort=` | user | → paged `SeriesSummary { id, title, editionCount }` |
| POST | `/series` | user | `{ title* }` → 201 `SeriesSummary` |
| GET | `/series/{id}` | user | → `SeriesDetail { id, title, editions: [EditionResponse] }` |
| PUT | `/series/{id}` | admin | `{ title* }` → `SeriesSummary` |
| DELETE | `/series/{id}` | admin | → 204 |
| POST | `/series/{seriesId}/editions` | user | `EditionRequest` → 201 `EditionResponse` |
| GET | `/editions/{id}` | user | → `EditionResponse` |
| PUT | `/editions/{id}` | admin | `EditionRequest` with `version*` → `EditionResponse` |
| DELETE | `/editions/{id}` | admin | → 204 (409 if it is in a purchase list) |

- `EditionRequest { name*, type*, status*, volumeCount, publisherId, version }`. `publisherId` is optional
  (`null` = no publisher); an unknown id → 400 on `publisherId`.
- `EditionResponse { id, seriesId, seriesTitle, name, publisherId, publisher, type, status, volumeCount,
  hasCover, canEditCover, inMyCollection, collectionCount, version }`.
- **Editing an edition**: send back the `version` you read. If someone changed it in the meantime → 409; read it
  again and retry.

### Covers

| Method | Path | Who | Body → Response |
|---|---|---|---|
| GET | `/editions/{id}/cover` | user | → `image/jpeg` (404 when there is none) |
| PUT | `/editions/{id}/cover` | admin or the edition's creator | multipart field `file` (an image, max 5 MB) → 204 |
| DELETE | `/editions/{id}/cover` | admin or the edition's creator | → 204 |

- The server converts uploads to JPEG and scales them down (max 480×720).
- `canEditCover` in `EditionResponse` says whether the current user may change it. Cache the image and reload
  it when the edition's `version` changes.

### Publishers

| Method | Path | Who | Body → Response |
|---|---|---|---|
| GET | `/publishers` | user | → `[ { id, name } ]`, sorted by name |
| POST | `/publishers` | admin | `{ name* }` → 201 `{ id, name }` |
| PATCH | `/publishers/{id}` | admin | `{ name* }` → `{ id, name }` |
| DELETE | `/publishers/{id}` | admin | → 204 (409 if editions use it) |

Names are unique ignoring case and edge spaces (duplicate → 400 on `name`), max 100 characters.

### Collection (owner)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/collection?q=&type=&status=&incomplete=&page=&size=` | → paged `CollectionEntry` |
| GET | `/collection/editions/{editionId}` | → `CollectionEntry` |
| PUT | `/collection/editions/{editionId}` | → `CollectionEntry` (adds the edition; idempotent) |
| PUT | `/collection/editions/{editionId}/volumes` | `{ owned*: [1, 2, 5] }` → `CollectionEntry` (the full set of owned volumes) |
| DELETE | `/collection/editions/{editionId}` | → 204 |
| GET | `/collection/stats` | → `CollectionStats` |

- `CollectionEntry { editionId, editionName, seriesId, seriesTitle, publisher, type, status, volumeCount,
  owned: [int], hasCover, version }`.
- `CollectionStats { editions, series, ownedVolumes, totalVolumes, completedEditions, byPublisher, byType,
  byStatus }`; each `by*` is `[ { key, volumes } ]` (`byPublisher`: top 5, then `key: null` = others).

### Purchase lists (owner)

Private to their owner: another user's list answers 404.

| Method | Path | Body → Response |
|---|---|---|
| GET | `/purchase-lists` | → `[ListSummary]` (newest month first) |
| POST | `/purchase-lists` | `{ description*, month* (1–12), year* (2000–2100) }` → 201 `ListDetail` |
| GET | `/purchase-lists/{id}` | → `ListDetail` |
| PATCH | `/purchase-lists/{id}` | `ListPatch` → `ListDetail` |
| DELETE | `/purchase-lists/{id}` | → 204 (open lists only, otherwise 409) |
| POST | `/purchase-lists/{id}/close` | → `ListDetail` (booked rows become owned volumes) |
| POST | `/purchase-lists/{id}/reopen` | → `ListDetail` (those volumes become missing again, unless another closed list has them) |
| POST | `/purchase-lists/{id}/items` | `ItemRequest` → 201 `ListDetail` |
| PATCH | `/purchase-lists/{id}/items/{itemId}` | `ItemPatch` → `ListDetail` |
| DELETE | `/purchase-lists/{id}/items/{itemId}` | → `ListDetail` |
| POST | `/purchase-lists/{id}/items/move` | `{ itemIds*: [uuid], targetListId*, releaseDate }` → `ListDetail` (of the source list) |
| GET | `/purchase-lists/{id}/proposal?editionId=` | → `Proposal { volume, priceEurCents, releaseDate }` (values to prefill a new row) |

- `ListSummary { id, description, month, year, closed, itemCount, unbookedCount, totalCents }`.
- `ListDetail { id, description, month, year, closed, items: [ItemView], subtotalCents, discountCents,
  discountAuto, pointsDiscountCents, totalCents }`. Total = subtotal − discount − points discount, never below 0.
- `ListPatch { description, month, year, discountCents, discountAuto, pointsDiscountCents }`: send only what
  changes. `discountAuto: true` goes back to the automatic discount (percentage from the purchase settings); a
  `discountCents` fixes it.
- `ItemRequest { editionId*, volume* (1–9999), priceEurCents*, priceChfCents*, releaseDate, booked }`;
  `ItemPatch` has the same fields, all optional, plus `clearReleaseDate: true` to remove the date.
- `ItemView { id, editionId, editionName, seriesId, seriesTitle, editionVolumeCount, volume, releaseDate,
  priceEurCents, priceChfCents, booked, alsoIn: [list description] }` (`alsoIn`: other lists with the same
  volume).
- A row above the edition's volume count raises it in the shared catalog.

### Spending summary (owner)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/purchase-summary?year=` | → `SpendingSummary` |

- `year` is optional (2000–2100, else 400). If that year has no lists, the answer uses the most recent year with
  lists; `year` in the answer says which one.
- `SpendingSummary { years: [int], year, months: [MonthSpend ×12], monthTotals: SpendTotals, byYear:
  [YearSpend], yearTotals: SpendTotals }`.
- `MonthSpend { month, spentCents, upcomingCents, hasLists, aboveAverage }`: spent = closed lists, upcoming =
  open lists, each list counted with its total in its month/year.
- `SpendTotals { totalCents, averageCents, spentCents, upcomingCents }`; the average divides by the months (or
  years) that have spending.

### Account (owner)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/account/purchase-settings` | → `{ chfRate, discountPercent }` |
| PUT | `/account/purchase-settings` | `{ chfRate*, discountPercent* (0–100) }` → same |
| GET | `/account/export` | → `AccountExport` (all the user's data as JSON) |
| DELETE | `/account` | `{ password* }` → 204, deletes the account and its data and ends the session (409 for the last admin) |

### Users (admin)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/users?page=&size=` | → paged `UserResponse { id, username, role, mustChangePassword, createdAt }` |
| POST | `/users` | `{ username*, role*, temporaryPassword* }` → 201 `UserResponse` |
| PATCH | `/users/{id}` | `{ role* }` → `UserResponse` (409 on your own role, or demoting the last admin) |
| POST | `/users/{id}/reset-password` | `{ temporaryPassword* }` → 204 (the user must change it at next login) |
| DELETE | `/users/{id}` | → 204 (409 for yourself — use `DELETE /account` — or the last admin) |

## Example: add a volume to a purchase list

```http
POST /api/v1/purchase-lists/0190b3c2-…/items
Cookie: SESSION=…; XSRF-TOKEN=…
X-XSRF-TOKEN: …
Content-Type: application/json

{ "editionId": "0190a000-…", "volume": 12, "priceEurCents": 690, "priceChfCents": 830,
  "releaseDate": "2026-11-20", "booked": true }
```

```http
201 Created

{ "id": "0190b3c2-…", "description": "Novembre", "month": 11, "year": 2026, "closed": false,
  "items": [ { "id": "0190b3d0-…", "volume": 12, "priceChfCents": 830, "booked": true, … } ],
  "subtotalCents": 830, "discountCents": 80, "discountAuto": true, "pointsDiscountCents": 0,
  "totalCents": 750 }
```

## Keeping this up to date

When an endpoint changes: update this guide, then regenerate `openapi.json` (the `OpenApiDocIT` test tells you
when it is out of date):

```bash
cd backend && ./mvnw verify -Dit.test=OpenApiDocIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dopenapi.update=true
```
