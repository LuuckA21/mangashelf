# Purchase lists — design

Date: 2026-11-05 · Status: approved in conversation, awaiting written review

## Goal

Replace the user's monthly purchase spreadsheet. Each user keeps **purchase lists** (one per shop receipt,
usually one per month) of catalog volumes with release date and prices; closing a list marks its booked
volumes as owned in the collection. Lists are private to their owner.

This is part 1 of 2. Part 2 (spending summaries: per month/year, per series, savings from discounts) gets
its own spec later and builds on this data. Not in scope: importing the spreadsheet (lists are re-entered by
hand), adding a range of volumes at once, sharing lists with other users.

## The user's spreadsheet, as the reference

One sheet per month = one receipt. Rows grouped by **release date**; each row: manga, volume (or "Unico"),
price EUR (cover price, informative), price CHF (what is paid). Bottom: subtotal CHF, discount, points
discount, total. Yellow rows = booked at the shop; non-yellow = expected but not booked yet. When a manga
slips, the user creates the next list and moves the row there.

Observed rules: CHF = EUR × 1.2 rounded to 0.10; discount = 10% of subtotal rounded to 0.10.

## Rules

### Account settings (new)

- `EUR→CHF factor`, default **1.20**; `discount %`, default **10**. Per user, editable on the Account page.
  Used to propose the CHF price of new rows and to compute a list's automatic discount.

### List

- **Description** and **month/year**: both required; month/year is indicative only (no rule depends on it
  except sorting).
- State **Aperta** (open) or **Chiusa** (closed). New lists are open.
- **Subtotal** = sum of the CHF price of **all** rows, booked or not.
- **Discount**: automatic = subtotal × discount % / 100, rounded to the nearest 0.10, recomputed whenever rows
  change. The user can type a fixed CHF amount instead; it then stays fixed when rows change, until the user
  goes back to automatic.
- **Points discount**: CHF amount typed by the user, default 0.
- **Total** = subtotal − discount − points discount, **never below 0**:
  - prices, discount and points discount are ≥ 0 (validation);
  - saving a fixed discount or points discount that would make the total negative is refused (400 on the
    field);
  - if removing/moving rows later makes discounts exceed the subtotal, discounts stay as they are and the
    total is shown and counted as **0**.
- **Close**: if the list has non-booked rows, the UI asks for confirmation ("N righe non prenotate: non
  diventeranno possedute e restano nel totale. Chiudere comunque?").
- **Reopen**: allowed; effects on the collection below.
- **Delete**: only when **open**, after confirmation; deletes its rows.

### Row

- **Edition** (from the catalog, always) and **volume number** (≥ 1): required.
- **Release date**: optional.
- **Price EUR**: required, ≥ 0. **Price CHF**: required, ≥ 0; the form proposes EUR × factor rounded to the
  nearest 0.10 and recomputes it when EUR changes, unless the user has edited CHF by hand.
- **Booked** (prenotata): yes/no, **no** by default.
- An edition with exactly 1 volume shows "Unico" instead of "1".
- Amounts are stored as integer cents.
- **Proposed values** when the user picks an edition in the add form:
  - volume = 1 + the highest volume of that edition the user owns or has in any of their lists (1 if none);
  - price EUR = EUR price of the user's most recently created row for that edition (empty if none);
  - date = release date of the most recently created row of this list (empty if none).
- **Duplicates** allowed: the same edition+volume may be in several lists. When a row is added or moved and
  the same edition+volume is in another of the user's lists, the UI shows "Già nella lista «X»".
- **Catalog**: adding or editing a row whose volume is above the edition's volume count raises the
  edition's volume count to that volume (the catalog is shared, so for everyone).

### Collection sync (applied in the same transaction as the action)

A volume is **acquired** when it is a **booked** row in a **closed** list.

- Becomes **owned** when it becomes acquired: closing a list (all its booked rows); in a closed list, adding
  a booked row, marking a row booked, moving a booked row in, or changing a booked row's edition/volume
  (the new one). The edition is added to the user's collection if missing.
- Becomes **missing** when it stops being acquired: reopening a list (all its booked rows); in a closed list,
  deleting a booked row, un-marking booked, moving a booked row out, or changing a booked row's
  edition/volume (the old one) — **unless** the same edition+volume is still acquired through another of the
  user's closed lists. It becomes missing even if the user had ticked it by hand before.
- Ticking volumes by hand on the edition page keeps working as today and does not change any list.
- Changing the edition or volume of a booked row in a closed list = the old volume stops being acquired, the
  new one becomes acquired (rules above).

### Catalog safeguards

- Deleting an edition that appears in any purchase list is refused (409, "L'edizione è in una lista
  acquisti").
- Lowering an edition's volume count below a volume present in any purchase list is refused, like the
  existing check for owned volumes.

### Privacy

- Lists and rows are visible only to their owner; any other user gets **404** on them.
- The account data export includes the user's lists, rows and purchase settings.
- Deleting the account deletes lists and rows (FK cascade).
- `docs/privacy.md` gets the new data category (purchases: what was bought, when, prices, discounts) under
  the existing purpose.

## UI

- Menu: new item **Acquisti** between Catalogo and Account.
- `/purchases`: button **Nuova lista** (form: description, month/year). Lists sorted by year/month
  descending, then by creation. Each shows description · month/year, state chip (Aperta/Chiusa), row count
  ("24 volumi, 2 non prenotati"), total CHF; clicking opens it.
- `/purchases/:id`:
  - Header: description and month/year (editable), **Chiudi lista** / **Riapri**, **Elimina** (open only).
  - **Aggiungi volume** form: edition (search by series or edition), volume, date, EUR, CHF, with the
    proposed values above.
  - Rows grouped by release date ("20 agosto"), groups ascending; rows without date last under "Data da
    definire"; within a date by series title, edition name, volume.
  - Each row: selection checkbox, edition name, volume (or "Unico"), EUR, CHF, **Prenotata** toggle (booked
    rows highlighted), **Modifica**, **Sposta**, **Elimina**.
  - Selecting rows shows **Sposta selezionate**. Move dialog: target list (any other list of the user) and an
    optional new date applied to all moved rows; empty keeps each row's date.
  - Bottom: subtotal, discount (with **modifica** and **torna al N%**), points discount (editable),
    **total** emphasised.
- Account page: new section **Acquisti** with factor and discount %.
- Phone (≤700px): rows on two lines (name + volume; prices + actions), totals at the bottom, 44px targets.
- Confirmations before close-with-unbooked, delete row, delete list. Errors as in the rest of the app.

## Build

### Database (new Flyway migrations, backwards-compatible)

- `users`: `chf_rate numeric(4,2) not null default 1.20`, `discount_percent int not null default 10`
  (checks: rate > 0, percent 0–100).
- `purchase_lists`: `id uuid`, `user_id` (FK users, on delete cascade), `description text`, `month int`
  (1–12), `year int`, `closed boolean`, `discount_cents int null` (null = automatic),
  `points_discount_cents int not null default 0`, `created_at`, `updated_at`. Index on `user_id`.
- `purchase_items`: `id uuid`, `list_id` (FK, on delete cascade), `edition_id` (FK editions, on delete
  restrict), `volume int` (≥ 1), `release_date date null`, `price_eur_cents int`, `price_chf_cents int`
  (≥ 0), `booked boolean`, `created_at`, `updated_at`. Indexes on `list_id`, `edition_id`.

### Backend

- New package `purchases` (entities, repositories, `PurchaseService`, `PurchaseController`, `dto/` records),
  same style as `catalog` and `collection`.
- Endpoints (all for the logged-in user, owner check → 404):
  - `GET/POST /api/v1/purchase-lists`; `GET/PATCH/DELETE /api/v1/purchase-lists/{id}`
    (PATCH: description, month, year, discount mode/amount, points discount);
  - `POST /api/v1/purchase-lists/{id}/close`, `POST /api/v1/purchase-lists/{id}/reopen`;
  - `POST /api/v1/purchase-lists/{id}/items`, `PATCH/DELETE /api/v1/purchase-lists/{id}/items/{itemId}`;
  - `POST /api/v1/purchase-lists/{id}/items/move` (item ids, target list id, optional date);
  - `GET /api/v1/purchase-lists/{id}/proposal?editionId=` (proposed volume, EUR, date);
  - `GET/PUT /api/v1/account/purchase-settings`.
- List detail returns rows (with edition/series names, edition volume count, "also in" list names),
  subtotal, discount (amount + automatic flag), points, total — computed server-side.
- Collection sync and catalog bump/safeguards live in `PurchaseService` / `EditionService`, one transaction
  per action, using the existing `owned_volumes` / `collection_editions` tables.
- Rounding: nearest 0.10, exact halves up (9.95 → 10.00), on cents: `round(x / 10) * 10`.
- Problem Details errors; Bean Validation at the API boundary; parameterized queries only.

### Frontend

- `features/purchases/` (list page, detail page, forms, API hooks), Account section, menu item, strings in
  `i18n/it.ts`, styles with existing tokens and `docs/frontend-style.md` rules.

### Tests

Backend integration tests (Testcontainers) for: create/edit/delete list (delete refused when closed);
row validation; proposed values; CHF and discount rounding; fixed vs automatic discount; close/reopen sync;
add/move/delete/un-book/edit rows in a closed list; volume acquired via two closed lists; "missing even if
ticked by hand"; catalog bump; edition delete and volume-count lowering refused; other user's list → 404;
export includes purchases; account deletion removes them; settings defaults and update.

Frontend (Vitest + Testing Library + axe): lists page, create list, add row with proposals and CHF
recompute, booked toggle, totals and discount override, move selected rows, close with unbooked
confirmation, settings section, accessibility.

### Docs

`docs/privacy.md`, README (feature list), `CHANGELOG.md` under Unreleased.
