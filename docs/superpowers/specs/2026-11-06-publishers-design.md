# Publishers as a managed list — design

Date: 2026-11-06 · Status: approved in conversation, awaiting written review

## Goal

Replace the free-text publisher of an edition with a choice from a shared list of publishers. Only admins
add, rename or delete publishers, on a new general **Amministrazione** page meant to host more admin sections
later. Out of scope: filtering the catalog or collection by publisher, merging publishers, edition counts on
the admin page.

## Rules

### Publisher

- **Name**: required, ≤ 100 characters, stored stripped of leading/trailing spaces.
- **Unique** ignoring case and edge spaces ("Star Comics" = "star comics "): adding or renaming to a used
  name fails with "Editore già presente" on the name field.
- Only **admins** add, rename or delete; any other user gets 403. Every logged-in user can read the list
  (needed by the edition form).
- **Delete** only when no edition uses it; otherwise 409 "L'editore è usato da alcune edizioni".

### Edition

- The publisher stays **optional**: any user creating or editing an edition picks one from the list, or none.
- A publisher id that does not exist → 400 on field `publisherId` ("Editore non trovato").
- A rename shows everywhere at once (catalog, collection, statistics chart, export, purchase lists).

### Existing data

- All existing editions start **without a publisher**; the list starts **empty**. The admin creates the
  publishers and assigns them.
- The old free-text column `editions.publisher` stays in the database, no longer read or written, so the
  previous release can run against the migrated schema (rollback). A later release drops it.

### Statistics

- "Per editore" groups by publisher; editions without one count in **Altri**, as today.

## UI

- Menu: **Amministrazione** (admin only) after Utenti: Collezione · Catalogo · Acquisti · Utenti ·
  Amministrazione · Account. A non-admin opening `/admin` gets the same behaviour as `/admin/users` today.
- `/admin`: title "Amministrazione", then one `.panel` per section, stacked; today only **Editori**.
- Editori section: a "Nome" field with **Aggiungi** (field errors under it); the publishers sorted
  alphabetically (case-insensitive), each with its name, **Rinomina** (inline field with **Salva** /
  **Annulla**) and **Elimina** (confirmation via the existing ConfirmDelete; the 409 message shown).
- Edition form: the **Editore** field becomes a search field with suggestions, using the browser's
  `<input list>` + `<datalist>` of publisher names. Empty = no publisher. A name not in the list
  (compared ignoring case and edge spaces) → field error "Scegli un editore dalla lista". Editing an edition
  with a publisher shows its name.
- Everywhere else the publisher name shows where it shows today.

## Build

### Database (new Flyway migration, backwards-compatible)

- `publishers`: `id uuid` (pk `pk_publishers`), `name text not null` (check length 1–100), `created_at`,
  `updated_at` `timestamptz not null`; unique index `uq_publishers_name` on `lower(name)`.
- `editions.publisher_id uuid null`, FK `fk_editions_publisher_id` → `publishers(id)` ON DELETE RESTRICT,
  index `ix_editions_publisher_id`. `editions.publisher` untouched.

### Backend

- New package `publishers` (entity, repository, service, controller, dto) in the style of `catalog`.
- `GET /api/v1/publishers` → `[{id, name}]` sorted by `lower(name)`; `POST /api/v1/publishers` `{name}` → 201
  with `Location`; `PATCH /api/v1/publishers/{id}` `{name}`; `DELETE /api/v1/publishers/{id}` → 204. Admin
  check like `SeriesService.requireAdmin`.
- `EditionRequest`: `publisherId` (UUID, nullable) replaces `publisher`. `EditionResponse`: `publisherId` and
  `publisher` (name, nullable).
- Every query that read `e.publisher` (catalog, collection list/entry, statistics, export, purchase rows if
  any) joins `publishers` and reads its name.
- Problem Details errors; Bean Validation; parameterized queries.

### Frontend

- Types `Publisher`; hooks `usePublishers`, admin mutations; `features/admin/AdminPage.tsx` with
  `PublishersSection`; route `/admin` guarded like `/admin/users`; nav item; `EditionForm` publisher field;
  strings in `i18n/it.ts`.

### Tests

Backend integration: add/rename/delete; non-admin 403 on writes, 200 on read; duplicate name rejected with
different case/spaces (add and rename); delete in use → 409; edition with publisher, without, unknown id →
400; rename visible in edition response, collection entry and statistics; existing editions have no
publisher after migration.

Frontend: admin page (add, rename, cancel, delete with confirmation, 409 message), nav item admin-only,
edition form (pick from suggestions sends `publisherId`, empty sends null, unknown name shows the error,
edit shows the current name), axe.

### Docs

`CHANGELOG.md` under Unreleased. `docs/privacy.md` unchanged (no personal data).
