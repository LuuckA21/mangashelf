# Purchase Spending Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A **Riepilogo** tab on `/purchases` with spending month by month for a chosen year and year by year, as tables and hand-made SVG column charts.

**Architecture:** One read-only endpoint `GET /api/v1/purchase-summary` in package `purchases`, computed in Java from the existing `PurchaseService.lists(userId)` totals (no new query on items, no migration). Frontend: tabs in `PurchasesPage` driven by `useSearchParams`, a `SummaryTab` with a year select and two blocks (Mesi, Anni), each a `ColumnChart` + `SummaryTable`.

**Tech Stack:** Java 25, Spring Boot 4.1, JdbcClient, PostgreSQL 17, Testcontainers · React 19, TypeScript strict, TanStack Query, react-router, Vitest, Testing Library, vitest-axe.

**Spec:** `docs/superpowers/specs/2026-11-07-purchase-summary-design.md`

## Global Constraints

- Amount of a list = existing list total (`ListSummary.totalCents`, never below 0); each list counts whole in its own month/year. Spent = closed lists, upcoming = open lists. Only the caller's lists.
- Months: always 12. `hasLists` false → "—" in both columns; true → amounts, CHF 0.00 included.
- Average = TOTALE ÷ count of entries (months, or years) whose spent + upcoming > 0; 0 when none. Rounded to the nearest cent, half up (`(total * 2 + n) / (2 * n)` in longs, or `Math.floorDiv` equivalent; no floating point).
- Above average: spent + upcoming **strictly greater** than the average. A month without lists is never above.
- Years: those with ≥ 1 list, ascending. `year` param optional, `@Min(2000) @Max(2100)` → 400 outside; if absent or without lists → most recent year with lists; no lists at all → `year: null`, `years: []`, `byYear: []`, 12 empty months, totals 0.
- Endpoint: 401 without login, 403 while the password must change (existing filter). DTO records; Problem Details.
- Summary amounts only: thousands with apostrophe `CHF 16'062.10`. Elsewhere `formatChf` unchanged.
- URL: `/purchases?vista=riepilogo&anno=2025`; no `vista` = Liste; no `anno` = request with the device's current year (server falls back).
- UI text Italian via `t()`; `docs/frontend-style.md`; 44px targets ≤ 700px; axe clean. No new dependency.
- Backend green: `mvn spotless:apply && mvn verify`. Frontend green: `npm run lint && npm run typecheck && npm test && npm run format:check && npm run build`.

## Review Focus

1. A list whose discounts exceed its subtotal must count 0, not negative, in month and year totals — Task 1 `clampedListCountsZero`.
2. Months with lists totalling 0 must not count in the average divisor, and show CHF 0.00, not "—" — Task 1 `zeroMonthWithListsShowsButIsNotAveraged`, Task 2 `dashOnlyForMonthsWithoutLists`.
3. Changing a list (add row, close, reopen, delete) or the discount % in settings must refresh the summary — Task 2 `summaryRefreshesAfterAListChange` (invalidation of `['purchase-summary']`).
4. Opening `?vista=riepilogo` with an `anno` that has no lists must show the year the server chose, and the select must show that year — Task 2 `unknownYearShowsTheServerYear`.
5. Averages must round half up on exact half cents (e.g. 100.01 + 100.00 over 2 months → 100.01, `20001 / 2` → `10001`) — Task 1 `averageRoundsHalfUp`.

---

### Task 1: Backend — summary endpoint

**Files:**
- Create in `backend/src/main/java/me/luucka/mangashelf/purchases/`: `PurchaseSummaryService.java`, `dto/SpendingSummary.java` (records `SpendingSummary`, `MonthSpend`, `YearSpend`, `SpendTotals`, nested or separate files, house style)
- Modify: `PurchaseController.java` (new GET; `@Validated` on the controller if needed for `@Min/@Max` on the param)
- Test: `backend/src/test/java/me/luucka/mangashelf/purchases/PurchaseSummaryIT.java` (setup like `PurchaseListsIT`)

**Interfaces:**
- `record SpendTotals(long totalCents, long averageCents, long spentCents, long upcomingCents)`
- `record MonthSpend(int month, long spentCents, long upcomingCents, boolean hasLists, boolean aboveAverage)`
- `record YearSpend(int year, long spentCents, long upcomingCents, boolean aboveAverage)`
- `record SpendingSummary(List<Integer> years, Integer year, List<MonthSpend> months, SpendTotals monthTotals, List<YearSpend> byYear, SpendTotals yearTotals)`
- `GET /api/v1/purchase-summary?year={int?}` → `SpendingSummary`.
- `PurchaseSummaryService.summary(UUID userId, Integer year) : SpendingSummary`, using `PurchaseService.lists(userId)`.

- [ ] **Step 1: Failing tests** in `PurchaseSummaryIT` (create lists/rows/close through the API like `PurchaseListsIT`): `noListsIsEmpty`; `closedIsSpentOpenIsUpcoming`; `listTotalAfterDiscountsIsTheAmount` (subtotal 100.00, 10% → 90.00 in its month); `clampedListCountsZero` (points discount saved then rows removed → 0, month `hasLists` true); `zeroMonthWithListsShowsButIsNotAveraged` (months: 100.00, 50.00, one list at 0 → average 75.00); `averageRoundsHalfUp`; `aboveAverageIsStrictlyGreater` (two months equal → neither above); `yearsAscendingWithTheirAverage` (2025 and 2026 lists; `byYear` order, `yearTotals.averageCents`, flags); `requestedYearWithoutListsFallsBackToMostRecent`; `missingYearFallsBackToMostRecent`; `yearOutOfRangeIs400` (1999, 2101); `otherUsersListsNotCounted`; `requiresLogin` (401); `isForbiddenWhileThePasswordMustChange` (403).
- [ ] **Step 2: Run** `mvn verify -Dit.test=PurchaseSummaryIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` → FAIL (404).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `mvn spotless:apply && mvn verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** `feat(purchases): spending summary by month and year`

---

### Task 2: Frontend — tabs, year picker, tables

**Files:**
- Modify: `frontend/src/api/types.ts` (types mirroring Task 1), `features/purchases/api.ts` (`usePurchaseSummary(year: number)`, query key `['purchase-summary', year]`; add `'purchase-summary'` to every invalidation that touches `'purchase-lists'` and to `useSavePurchaseSettings`), `features/purchases/money.ts` (`formatChfGrouped(cents): string` → `CHF 16'062.10`), `features/purchases/PurchasesPage.tsx` (tabs), `i18n/it.ts`, `styles.css`
- Create: `features/purchases/SummaryTab.tsx`, `features/purchases/SummaryTable.tsx`
- Test: `features/purchases/summary.test.tsx` (fetch mock like `purchases.test.tsx`)

**Interfaces:**
- Consumes: Task 1 JSON.
- Produces: `SummaryTable({ rows: { label: string; fullLabel: string; spentCents: number; upcomingCents: number; hasLists: boolean; aboveAverage: boolean }[]; totals: SpendTotals; firstColumn: string })` and the layout slot `.summary-block` with `.summary-chart` (left / above) and the table (right / below); Task 3 fills `.summary-chart`.
- Tabs: WAI-ARIA tabs (`role="tablist"`, two `role="tab"` buttons with `aria-selected`, `aria-controls`, arrow keys move between them; panel `role="tabpanel"`). Labels "Liste", "Riepilogo". Selecting sets `vista` (removes it for Liste); select "Anno" sets `anno`.
- Strings (Italian): "Liste", "Riepilogo", "Anno", "Mesi", "Anni", "Mese", "Speso", "In arrivo", "TOTALE", "Media", "Totale speso", "Totale in arrivo", "sopra la media", "Ancora nessuna spesa"; full month names via `Intl` it-IT capitalised.

- [ ] **Step 1: Failing tests:** `tabsSwitchAndLiveInTheUrl` (Riepilogo → `?vista=riepilogo`, Liste hides the summary and shows Nuova lista; arrow keys move focus between tabs); `requestsTheCurrentYearFirst` (fake Date 2026 → GET `/purchase-summary?year=2026`); `yearPickerChangesUrlAndData`; `unknownYearShowsTheServerYear` (`anno=2020`, server answers `year: 2026` → select shows 2026); `monthTableShowsBothColumnsAndTotals` (apostrophe: `CHF 1'642.38`); `dashOnlyForMonthsWithoutLists`; `aboveAverageRowsAreMarked` (row class + "sopra la media" text); `yearsTable`; `noListsSaysSo` ("Ancora nessuna spesa", no select); `summaryRefreshesAfterAListChange` (spy on `invalidateQueries` like existing tests: includes `['purchase-summary']`); `formatChfGrouped` unit cases (0 → `CHF 0.00`, 123456789 → `CHF 1'234'567.89`); axe on the Riepilogo tab.
- [ ] **Step 2: Run** `npx vitest run src/features/purchases` → FAIL.
- [ ] **Step 3: Implement** (leave `.summary-chart` empty for Task 3).
- [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): spending summary tab with month and year tables`

---

### Task 3: Frontend — column charts; docs

**Files:**
- Create: `features/purchases/ColumnChart.tsx`
- Modify: `SummaryTab.tsx` (render a chart in each `.summary-chart`), `styles.css`, `i18n/it.ts`, `summary.test.tsx`, `CHANGELOG.md` (Unreleased → Added)

**Interfaces:**
- `ColumnChart({ label: string; columns: { short: string; full: string; spentCents: number; upcomingCents: number; aboveAverage: boolean }[]; averageCents: number })`.
- SVG `role="img"` with `aria-label` = label ("Spesa per mese, 2026" / "Spesa per anno"); stacked rect per column: spent (solid `--indigo`) at the bottom, upcoming above it (lighter, diagonal-stripe `<pattern>`); dashed average line with its `formatChfGrouped` value; ▲ above columns with `aboveAverage`; short labels under columns (G F M A M G L A S O N D, or the years). Legend (Speso / In arrivo) above, outside the SVG. Scale: tallest column (or the average, if higher) = full height; all zero → flat columns, no line.
- Tooltip: each column has an invisible full-height hit `<rect>` handling `pointerenter` (mouse) and `click` (tap); it shows a box `"{full}: speso {CHF} · in arrivo {CHF}"`; `pointerleave` / tapping elsewhere hides it. No keyboard focus inside the `role="img"`: keyboard and screen-reader users get the same values from the table beside it.
- Strings: "Spesa per mese, {year}", "Spesa per anno", "{name}: speso {spent} · in arrivo {upcoming}".

- [ ] **Step 1: Failing tests:** `chartHasALabelAndOneColumnPerMonth` (12 hit areas); `hoverShowsTheValues` (pointer over November → "Novembre: speso CHF 0.00 · in arrivo CHF 147.78"); `aboveAverageColumnsHaveTheMarker`; `averageLineShowsItsAmount`; `allZeroDrawsNoAverageLine`; axe still clean.
- [ ] **Step 2: Run** `npx vitest run src/features/purchases` → FAIL.
- [ ] **Step 3: Implement**; CHANGELOG entry.
- [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): spending column charts`
- [ ] **Step 6: Visual check** (controller): Riepilogo with data from 2–3 years, computer and phone, hover tooltip; screenshots to the user.
