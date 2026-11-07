# Purchase spending summary — design

Date: 2026-11-07 · Status: approved in conversation, awaiting written review

## Goal

Part 2 of the purchases feature (part 1: `2026-11-05-purchase-lists-design.md`). Replace the user's
spreadsheet summaries: how much they spend **month by month** in a year and **year by year**, with tables
and column charts. Built only from existing purchase lists; no new stored data.

Out of scope (maybe later): spending per series or edition, savings from discounts, budgets.

## The user's spreadsheet, as the reference

- Per year: one row per month with the CHF amount and "Acquistato" SÌ/NO; below, TOTALE and Media
  (TOTALE ÷ months that have an amount: 1'642.38 ÷ 11 = 149.31). Months above the average are yellow.
- Per years: one row per year with the CHF amount; below, TOTALE and Media (TOTALE ÷ years with an
  amount, current year included: 16'062.10 ÷ 8 = 2'007.76). Years above the average are yellow.

## Rules

- **Amount of a list** = its existing total (subtotal − discount − points discount, never below 0), so
  non-booked rows of a closed list count, as they already do in the list total.
- Each list counts whole in its own **month/year** (the indicative month/year the user typed).
- **Spent** (Speso) = lists that are **closed**; **upcoming** (In arrivo) = lists that are **open**.
- Only the logged-in user's lists count.

### Months of the chosen year

- Always the 12 months, January…December, each with spent and upcoming. A month without lists shows "—"
  in both columns; a month with lists shows its amounts (CHF 0.00 included).
- Below: **TOTALE** = spent + upcoming of the year; **Media** = TOTALE ÷ number of months whose spent +
  upcoming is > 0 (0 when there is none); then **Totale speso** and **Totale in arrivo**.
- A month is **above the average** when its spent + upcoming is strictly greater than the Media.

### Years

- One row per year that has at least one list, ascending, each with spent and upcoming.
- Below: TOTALE, **Media** = TOTALE ÷ number of years whose spent + upcoming is > 0 (current year included;
  0 when none), Totale speso, Totale in arrivo.
- A year is above the average when its spent + upcoming is strictly greater than the Media.

### Choosing the year

- The choices are the years that have at least one list.
- Opening the summary shows the current year (device date) if it has lists, otherwise the most recent year
  with lists.

### Rounding and format

- Amounts are integer cents. Averages round to the nearest cent, half a cent up.
- In the summary only, amounts use the apostrophe as thousands separator: `CHF 16'062.10`. The rest of the
  app keeps `CHF 16062.10`.

## UI

- `/purchases` gets two tabs under the title: **Liste** (default: today's page) and **Riepilogo**.
  Pill-style buttons, the active one highlighted; keyboard and screen-reader accessible (tabs pattern).
  **Nuova lista** shows only on Liste.
- The tab and the year live in the URL: `/purchases?vista=riepilogo&anno=2025` (Back and bookmarks keep
  them). No `vista` = Liste; no `anno` = the default year above.
- **Riepilogo**, top to bottom:
  1. Year picker: a select with the years that have lists.
  2. Block **Mesi** (title), then block **Anni** (title). In each block, on a computer the chart is on the
     left and the table on the right; on a phone (≤ 700px) the chart is above the table, full width.
  3. No lists at all: the message "Ancora nessuna spesa" instead of picker and blocks.
- **Column chart** (hand-made SVG, no new library):
  - One column per month (labels G F M A M G L A S O N D) or per year (labels the years).
  - Each column stacked: **Speso** solid app blue, **In arrivo** lighter blue with diagonal stripes (not
    told apart by colour alone); legend above.
  - **Media**: dashed horizontal line with its amount beside it.
  - ▲ above the columns that are above the average.
  - Hover or tap on a column shows a box with its values, e.g. "Novembre: speso CHF 0.00 · in arrivo CHF
    147.78".
  - For screen readers the chart is an image with a short label ("Spesa per mese, 2026"); the table next to
    it carries the exact numbers.
- **Table**: compact rows, amounts right-aligned; columns Mese/Anno, Speso, In arrivo. Rows above the
  average have a light yellow background and ▲ with the text "sopra la media" for screen readers. Footer
  rows TOTALE, Media, Totale speso, Totale in arrivo, bold, separated by a line.
- Loading and errors as in the rest of the app ("Caricamento…", error message).

## Build

### Backend

- `GET /api/v1/purchase-summary?year=2026` in package `purchases`, for the logged-in user (401 without
  login, 403 while the password must change, as every other endpoint).
- `year` optional, `@Min(2000) @Max(2100)` like `ListRequest` (400 outside). If the requested year has no
  lists (or none is sent), the response uses the most recent year with lists.
- Computed from `PurchaseService.lists(userId)` (the existing per-list totals); no new query on items.
- Response (DTO records):
  ```
  {
    "years": [2025, 2026],            // with lists, ascending
    "year": 2026,                     // the year shown; null when the user has no lists
    "months": [ {"month": 1, "spentCents": 0, "upcomingCents": 0, "hasLists": false, "aboveAverage": false}, … 12 ],
    "monthTotals": {"totalCents": …, "averageCents": …, "spentCents": …, "upcomingCents": …},
    "byYear": [ {"year": 2025, "spentCents": …, "upcomingCents": …, "aboveAverage": true}, … ],
    "yearTotals": {"totalCents": …, "averageCents": …, "spentCents": …, "upcomingCents": …}
  }
  ```
  With no lists: `years` and `byYear` empty, `year` null, 12 empty months, all totals 0.
- No migration. `docs/privacy.md` unchanged (derived from data already listed there); export unchanged.

### Frontend

- `features/purchases/`: hook `usePurchaseSummary(year)`, `SummaryTab` (picker + two blocks),
  `ColumnChart` (SVG), `SummaryTable`, `formatChfGrouped` (apostrophe) in `money.ts`; tabs in
  `PurchasesPage` driven by `useSearchParams`; strings in `i18n/it.ts`; styles in `styles.css`.

### Tests

- Backend integration: no lists; closed vs open split; list total with discounts and clamped at 0; months
  without lists vs with lists at 0; month average over months with spending only; years and their average;
  above-average flags (strictly greater); requested year without lists → most recent year; year outside
  2000–2100 → 400; other users' lists not counted; 401; 403.
- Frontend: tabs switch and are in the URL; year picker changes the URL and the data; amounts with the
  apostrophe; "—" for months without lists; ▲ and highlighted rows; "Ancora nessuna spesa"; chart tooltip
  on hover/focus; axe clean. Visual check on computer and phone.

### Docs

`CHANGELOG.md` under Unreleased → Added.
