# Collection statistics — design

Date: 2026-11-05 · Status: approved in conversation, awaiting written review

## Goal

Show each user numbers and bar charts about **their own** collection, beside the collection list,
using only data that already exists. No schema change, no new personal data, no other user's data.

Out of scope (decided): a "to buy" page (the collection's *Incomplete* filter already lists missing
volumes), additions over time, prices, read status, anything about other users. Publishers stay free text
(a publisher list is planned later); this feature does not normalize them.

## What the user sees

### Numbers

Always computed over the user's **whole collection**; search and filters on the page do not change them.

| Label (it) | Value |
|------------|-------|
| Volumi | owned volumes |
| Edizioni | editions in the collection (including those with 0 owned volumes) |
| Serie | distinct series among those editions |
| Completate | editions where owned = `volume_count` **and** status is `COMPLETED` (an ongoing edition never counts) |
| Mancanti | total volumes − owned volumes, where total = sum of `volume_count` over the collection's editions |
| Completamento | owned / total × 100, **rounded down** (411/412 → 99%), shown with `%` |

### Charts

Horizontal bars, one row per entry: name, bar, number of **owned volumes**. Bar length is relative to the
largest value in that chart. Entries with 0 owned volumes are not shown.

- **Per editore**: the 5 publishers with most owned volumes, descending, ties alphabetical (exact string
  comparison). Everything else — further publishers and editions with no publisher — is summed into
  **Altri**, shown last and only if > 0. Publisher names are counted exactly as written (case and spaces
  matter).
- **Per tipo**: Regolare, Variant, Giapponese (fixed order).
- **Per stato**: In corso, Concluse (fixed order).

Each entry is also readable as text ("Star Comics: 210 volumi"); bars are decorative (`aria-hidden`).

### Layout

- Wider than 700px: the page title spans the full width; below it two columns — left the existing search,
  filters, list and pagination; right a ~280px column holding a `.panel` titled **Statistiche**, starting
  level with the search form. The column is **sticky** (stays in view while the list scrolls); if it is
  taller than the viewport it scrolls inside itself.
- 700px and narrower: the panel goes **below** the list, after pagination; not sticky.
- Panel content: the numbers as label/value rows, then the three charts, each with a small heading.

### States

- Loading: the panel is not shown.
- Empty collection (0 editions): the panel is not shown.
- Error: the panel shows "Statistiche non disponibili" and a **Riprova** button, like the list's error.
- Ticking volumes, adding or removing an edition, or catalog edits that refresh the collection also
  refresh the statistics.

## How it is built

### Backend

- `GET /api/v1/collection/stats` in `CollectionController`, for the logged-in user only; 401 without a
  session, 403 while the password must be changed (existing filter).
- Response DTO `collection/dto/CollectionStats` (record):
  `editions`, `series`, `ownedVolumes`, `totalVolumes`, `completedEditions` (all `long`),
  `byPublisher`, `byType`, `byStatus` (each `List<StatBar>`), with `StatBar(String key, long volumes)`.
  - `byPublisher` key: the publisher name, or `null` for **Altri** (always last when present).
  - `byType` / `byStatus` key: the enum name (`REGULAR`, `VARIANT`, `JAPANESE` / `ONGOING`, `COMPLETED`),
    in enum order. Labels are translated in the frontend with `t()`.
  - Missing volumes and percentage are derived in the frontend.
- `CollectionService.stats(UUID userId)`: SQL aggregates with the existing `NamedParameterJdbcTemplate`
  style, parameterized. Publisher totals come from SQL grouped by publisher (nulls included); the top-5 +
  Altri cut and dropping zero entries happen in Java.

### Frontend

- `useCollectionStats()` in `features/collection/api.ts`, query key `['collection', 'stats']` so every
  existing `invalidateQueries({ queryKey: ['collection'] })` refreshes it.
- New `features/collection/CollectionStats.tsx`; `CollectionPage` wraps search/filters/list/pagination and
  the stats panel in a two-column layout.
- New strings in `i18n/it.ts`; styles in `styles.css` using the existing tokens (indigo bars, `--s` spacing,
  `.panel`), breakpoint 700px as in `docs/frontend-style.md`.

### Tests

Backend integration test (Testcontainers), `CollectionStatsIT`:
- empty collection → all zeros and empty bar lists;
- counts for editions, series (two editions of one series count once), owned, total;
- ongoing edition with all volumes owned is not completed; completed edition with all volumes is;
- 6+ publishers plus editions without publisher → top 5 by volumes (ties alphabetical) + Altri with the rest;
- entries with 0 owned volumes are absent; Altri absent when 0;
- another user's collection is not counted;
- no session → 401.

Frontend (Vitest + Testing Library):
- shows the numbers, missing volumes and rounded-down percentage;
- renders bar entries as text, Altri label for the `null` key;
- hidden when editions = 0;
- error shows the message and Riprova refetches;
- axe: no violations.

## Privacy

No new personal data field, purpose or processor: `docs/privacy.md` is unchanged.
