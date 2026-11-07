# Collection Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sticky "Statistiche" column beside the collection list (below it on phones) with numbers and bar charts about the logged-in user's own collection.

**Architecture:** One new read-only endpoint `GET /api/v1/collection/stats` computes aggregates in SQL inside `CollectionService`; Java applies the top-5 + "Altri" cut and drops zero entries. The frontend fetches it with TanStack Query under the `['collection', ...]` key prefix so existing invalidations refresh it, and renders a `CollectionStats` panel in a two-column layout on `CollectionPage`.

**Tech Stack:** Java 25, Spring Boot 4.1, JdbcClient, PostgreSQL 17, Testcontainers · React 19, TypeScript strict, TanStack Query, Vitest, Testing Library, vitest-axe.

**Spec:** `docs/superpowers/specs/2026-11-05-collection-stats-design.md`

## Global Constraints

- Only the logged-in user's data; no schema change; `docs/privacy.md` unchanged.
- Numbers always over the whole collection; page search and filters never change them.
- Completate = owned count equals `volume_count` **and** status `COMPLETED`.
- Completamento = `Math.floor(owned / total * 100)` followed by `%`.
- Bars count **owned volumes**; entries with 0 are omitted; bar width relative to the chart's largest value.
- Publisher chart: top 5 by volumes desc, ties by exact `String.compareTo`, names exactly as stored; the rest plus `null` publishers summed into one entry with key `null` ("Altri"), last, only if > 0.
- Type chart order `REGULAR, VARIANT, JAPANESE`; status chart order `ONGOING, COMPLETED`.
- All UI text through `t()` in `i18n/it.ts`; Italian only.
- Panel hidden while loading and when `editions === 0`; on error "Statistiche non disponibili" + Riprova.
- Layout: >700px two columns, stats column ~280px, sticky, scrolls inside itself when taller than the viewport; ≤700px panel below the list and pagination, not sticky.
- Bars `aria-hidden`; each entry readable as text "<label>: <n> volumi".
- Backend: `./mvnw spotless:apply` then `./mvnw verify` green. Frontend: `npm run lint && npm run typecheck && npm test && npm run format:check && npm run build` green.

## Review Focus

1. An ongoing edition with every volume owned (shows 12/12 in the list) must **not** count as Completata — pinned in Task 1 `ongoingWithAllVolumesIsNotCompleted`.
2. Publishers differing only in case/spaces ("Star Comics", "star comics ") are separate bars — pinned in Task 1 `publishersAreCountedExactlyAsWritten`.
3. The stats request failing must not hide or break the collection list, and the list's own error must stay the only `alert` when stats are fine — pinned in Task 2 `statsErrorKeepsTheList`.
4. Ticking a volume must refresh the numbers without reloading the page — pinned in Task 2 `refreshesAfterTickingAVolume`.
5. A short desktop window with a tall panel: the sticky column must scroll internally, never cut off its bottom — checked in Task 2 Step 8 (screenshot at 1280×600).

---

### Task 1: Backend stats endpoint

**Files:**
- Create: `backend/src/main/java/me/luucka/mangashelf/collection/dto/CollectionStats.java`
- Create: `backend/src/main/java/me/luucka/mangashelf/collection/dto/StatBar.java`
- Modify: `backend/src/main/java/me/luucka/mangashelf/collection/CollectionService.java` (add `stats`)
- Modify: `backend/src/main/java/me/luucka/mangashelf/collection/CollectionController.java` (add GET)
- Test: `backend/src/test/java/me/luucka/mangashelf/collection/CollectionStatsIT.java`

**Interfaces:**
- Produces (JSON, consumed by Task 2):
  `GET /api/v1/collection/stats` → `{ editions, series, ownedVolumes, totalVolumes, completedEditions: number, byPublisher, byType, byStatus: {key: string|null, volumes: number}[] }`
- Java: `record StatBar(String key, long volumes)`;
  `record CollectionStats(long editions, long series, long ownedVolumes, long totalVolumes, long completedEditions, List<StatBar> byPublisher, List<StatBar> byType, List<StatBar> byStatus)`;
  `CollectionStats CollectionService.stats(UUID userId)` (`@Transactional(readOnly = true)`);
  `static List<StatBar> CollectionService.topPublishers(Map<String, Long> volumesByPublisher)` (package-private, `null` map key allowed).

- [ ] **Step 1: Write the failing tests** in `CollectionStatsIT`, same setup as `CollectionIT` (users Admin/Anna/Bruno, sessions, `send`, `series`, an `edition(...)` helper that also sends `"publisher"` when non-null, add to collection with `PUT /api/v1/collection/editions/{id}`, set volumes with `PUT .../volumes`). `stats(who)` = `mvc.perform(get("/api/v1/collection/stats").cookie(who))`.

  - `emptyCollectionIsAllZeros`: Anna has nothing → `editions 0, series 0, ownedVolumes 0, totalVolumes 0, completedEditions 0`, `byPublisher`, `byType`, `byStatus` empty.
  - `countsEditionsSeriesAndVolumes`: series "Berserk" with editions A (Panini, REGULAR, ONGOING, 10 vol, owned 1–3) and B (Panini, VARIANT, COMPLETED, 2 vol, owned none); series "Blu" edition C (null publisher, REGULAR, COMPLETED, 4 vol, owned 1–4) → `editions 3, series 2, ownedVolumes 7, totalVolumes 16, completedEditions 1`; `byType` = `[REGULAR 7]` (VARIANT has 0, absent); `byStatus` = `[ONGOING 3, COMPLETED 4]`; `byPublisher` = `[Panini 3, null 4]`.
  - `ongoingWithAllVolumesIsNotCompleted`: ONGOING 3 vol, owned 1–3 → `completedEditions 0`.
  - `topFivePublishersPlusOthers`: owned volumes per publisher P1=6, P2=5, P3=4, "Aa"=3, "Ab"=3, P6=2, null=1 → `byPublisher` = `[P1 6, P2 5, P3 4, Aa 3, Ab 3, null 3]`.
  - `publishersAreCountedExactlyAsWritten`: "Star Comics" 2 vol owned, "star comics " 1 vol owned → two entries, keys exactly `"Star Comics"` and `"star comics "`.
  - `zeroEntriesAndEmptyOthersAreOmitted`: publisher "Solo" with 2 owned and publisher "Zero" with 0 owned → `byPublisher` = `[Solo 2]` (no `null` entry, no "Zero").
  - `otherUsersAreNotCounted`: Bruno owns 5 volumes of an edition; Anna's stats unchanged (`ownedVolumes 0`, `editions 0`).
  - `requiresLogin`: no cookie → 401.
  - `isForbiddenWhileThePasswordMustChange`: a user with `mustChangePassword = true` → 403.

- [ ] **Step 2: Run to verify they fail**
  Run (in `backend/`): `./mvnw verify -Dit.test=CollectionStatsIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`
  Expected: FAIL (404 on `/api/v1/collection/stats`).

- [ ] **Step 3: Implement** the records, `stats(userId)` and the controller method `@GetMapping("/api/v1/collection/stats") CollectionStats stats(@AuthenticationPrincipal AppUserPrincipal me)`.
  - Totals in one query over `collection_editions ce join editions e` for `ce.user_id = :userId`, with the owned count per row from `owned_volumes` (the existing `OWNED_COUNT` expression works): `count(*)`, `count(distinct e.series_id)`, `coalesce(sum(e.volume_count),0)`, `coalesce(sum(owned),0)`, `count(*) filter (where owned = e.volume_count and e.status = 'COMPLETED')`.
  - One grouped query each for publisher, type, status: owned volumes summed per group.
  - Type/status: walk `EditionType.values()` / `EditionStatus.values()` in order, keep entries > 0.
  - `topPublishers`: drop entries ≤ 0; sort non-null keys by volumes desc then `String.compareTo`; first 5 become bars; the rest plus the `null` key sum into `new StatBar(null, sum)` appended only if sum > 0.
  - Parameterized `JdbcClient` only, as in the existing code.

- [ ] **Step 4: Run to verify they pass**, then the whole backend suite
  Run: `./mvnw spotless:apply && ./mvnw verify`
  Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 5: Commit**
  `git commit -m "feat(collection): statistics endpoint for the user's own collection"`

---

### Task 2: Frontend stats panel and layout

**Files:**
- Modify: `frontend/src/api/types.ts` (add `StatBar`, `CollectionStats`)
- Modify: `frontend/src/features/collection/api.ts` (add `useCollectionStats`)
- Create: `frontend/src/features/collection/CollectionStats.tsx`
- Modify: `frontend/src/features/collection/CollectionPage.tsx` (two-column layout)
- Modify: `frontend/src/i18n/it.ts`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/features/collection/collection.test.tsx`

**Interfaces:**
- Consumes: Task 1 JSON.
- Types: `type StatBar = { key: string | null; volumes: number }`;
  `type CollectionStats = { editions: number; series: number; ownedVolumes: number; totalVolumes: number; completedEditions: number; byPublisher: StatBar[]; byType: StatBar[]; byStatus: StatBar[] }`.
- `useCollectionStats()` → `useQuery({ queryKey: ['collection', 'stats'], queryFn: () => api<CollectionStats>('/collection/stats') })`.
- `export function CollectionStats(): JSX.Element | null` (default export not needed).
- i18n keys (exact copy):
  `'stats.title': 'Statistiche'`, `'stats.volumes': 'Volumi'`, `'stats.editions': 'Edizioni'`, `'stats.series': 'Serie'`, `'stats.completed': 'Completate'`, `'stats.missing': 'Mancanti'`, `'stats.completion': 'Completamento'`, `'stats.byPublisher': 'Per editore'`, `'stats.byType': 'Per tipo'`, `'stats.byStatus': 'Per stato'`, `'stats.others': 'Altri'`, `'stats.bar': '{label}: {n} volumi'`, `'stats.unavailable': 'Statistiche non disponibili'`, `'stats.status.ONGOING': 'In corso'`, `'stats.status.COMPLETED': 'Concluse'`. Type labels reuse `type.REGULAR|VARIANT|JAPANESE`.

- [ ] **Step 1: Default reply.** In `collection.test.tsx` `beforeEach`, add `'GET /collection/stats': [200, stats({ editions: 0 })]` with a `stats(over)` fixture (all zeros, empty lists), so existing tests keep passing.

- [ ] **Step 2: Write the failing tests** (describe "collection stats", rendering `/collection` via `renderApp`):
  - `showsNumbersMissingAndRoundedDownPercent`: stats `{editions 14, series 9, ownedVolumes 411, totalVolumes 412, completedEditions 5}` → within `region` named "Statistiche": texts "411", "14", "9", "5", "1" (Mancanti) and "99%".
  - `rendersBarsAsText`: `byPublisher [{key:'Star Comics',volumes:210},{key:null,volumes:30}]`, `byType [{key:'REGULAR',volumes:240}]`, `byStatus [{key:'COMPLETED',volumes:240}]` → texts "Star Comics: 210 volumi", "Altri: 30 volumi", "Regolare: 240 volumi", "Concluse: 240 volumi".
  - `hiddenWhenCollectionIsEmpty`: `editions 0` → no region "Statistiche".
  - `statsErrorKeepsTheList`: stats reply `[500]`, collection page with one entry → entry "Deluxe" visible, "Statistiche non disponibili" visible; clicking its "Riprova" after switching the reply to 200 shows the numbers.
  - `refreshesAfterTickingAVolume`: render `<CollectionStats />` in a `QueryClientProvider` whose `QueryClient` the test holds; first reply `ownedVolumes 3`, then switch the reply to `ownedVolumes 4` and call `client.invalidateQueries({ queryKey: ['collection'] })` (exactly what the owned-volumes and add/remove mutations already do) → "4" appears as the Volumi value.
  - `statsPanelHasNoAxeViolations`: populated stats → `expect(await axe(container)).toHaveNoViolations()`.

- [ ] **Step 3: Run to verify they fail**
  Run (in `frontend/`): `npx vitest run src/features/collection/collection.test.tsx`
  Expected: new tests FAIL, existing ones PASS.

- [ ] **Step 4: Implement** types, hook, i18n keys and `CollectionStats.tsx`:
  - Returns `null` while pending or when `data.editions === 0`.
  - Error: `<section className="panel stats-panel">` with the `.error` div (same markup as the list's error: text + `button.quiet` "Riprova" calling `refetch`). Use `role="status"`, not `alert`, so the list's error stays the only alert.
  - Success: `<section className="panel stats-panel" aria-labelledby=…>` with `<h2>` "Statistiche"; numbers as a `<dl>` (label/value rows); three charts each `<h3>` + `<ul>`; each `<li>` holds the visible name and number, a `<span className="sr-only">` or equivalent with `t('stats.bar', …)` if the visible text differs, and a decorative bar `<span className="stat-bar" aria-hidden="true" style={{ width: pct + '%' }}>` where `pct = volumes / max * 100`.
  - Labels: publisher `key ?? t('stats.others')`; type `t(\`type.${key}\`)`; status `t(\`stats.status.${key}\`)`.

- [ ] **Step 5: Layout.** In `CollectionPage`, keep `.page-head` full width; wrap the rest in `<div className="collection-layout"><div className="collection-main">…search, chips, states, groups, pagination…</div><CollectionStats /></div>`. CSS: `.collection-layout { display:grid; grid-template-columns: minmax(0,1fr) 280px; gap: calc(var(--s)*4); align-items:start }`; `.stats-panel { position: sticky; top: <topbar height + var(--s)*2>; max-height: calc(100vh - <same top> - var(--s)*2); overflow:auto; margin-top:0 }`; bars indigo token, height ~8px, rounded. In `@media (max-width: 700px)`: `.collection-layout { grid-template-columns: 1fr }`, `.stats-panel { position: static; max-height: none }`. Use the existing tokens; no new colours.

- [ ] **Step 6: Run to verify they pass**, then all checks
  Run: `npm run lint && npm run typecheck && npm test && npm run format:check && npm run build`
  Expected: all green.

- [ ] **Step 7: Commit**
  `git commit -m "feat(frontend): collection statistics column"`

- [ ] **Step 8: Visual check** with the dev stack (postgres, backend, Vite) and the scratchpad screenshot script: desktop 1280×800, desktop 1280×600 (panel scrolls inside, bottom reachable), phone 390×844 (panel below pagination). Fix and amend in a follow-up commit if anything is off; send the screenshots to the user.
