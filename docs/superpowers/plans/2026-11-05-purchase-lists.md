# Purchase Lists Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Private per-user purchase lists (rows of catalog volumes with date, EUR/CHF prices, booked flag, discounts) whose closing marks booked volumes as owned, replacing the user's monthly spreadsheet.

**Architecture:** New backend package `purchases` (JPA entities, repositories, `PurchaseService`, `PurchaseController`, DTO records) on two new tables plus two settings columns on `users`. Collection sync goes through two new public methods on `CollectionService`; catalog bump and safeguards through `EditionService`. Frontend gets `features/purchases/` (lists page, list detail page) and an Account section.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring Data JPA, JdbcClient, Flyway, PostgreSQL 17, Testcontainers · React 19, TypeScript strict, TanStack Query, React Router, Vitest, Testing Library, vitest-axe.

**Spec:** `docs/superpowers/specs/2026-11-05-purchase-lists-design.md`

## Global Constraints

- Lists/rows visible only to their owner; another user's list or row → **404**.
- Amounts in the API and DB are **integer cents**; never negative. Total = max(0, subtotal − discount − points).
- Rounding to 0.10 = nearest, exact halves up (9.95 → 10.00), integer maths only.
- Proposed CHF = EUR × rate and automatic discount = subtotal × percent / 100, both rounded with `Money.roundTo10(numerator, denominator)` on integer cents (rate as hundredths: `eurCents × 120 / 100`), so no floating point touches money.
- Defaults: `chf_rate` **1.20**, `discount_percent` **10**; new rows `booked = false`; new lists open.
- List: description (required, ≤ 100 chars) and month (1–12) / year (2000–2100) required. Row: edition, volume ≥ 1, EUR ≥ 0, CHF ≥ 0 required; release date optional.
- "Acquired" = booked row in a closed list. Collection sync rules exactly as in the spec section *Collection sync*; a volume stops being owned only if no other closed list of the same user still acquires it, even if it was ticked by hand.
- Catalog: a row's volume above the edition's `volume_count` raises it; deleting an edition used by any row → 409 "L'edizione è in una lista acquisti"; lowering `volume_count` below a volume in any row → 409.
- Problem Details errors, Bean Validation at the boundary, parameterized queries only, DTO records only, APIs under `/api/v1`.
- Migrations: new files only, backwards-compatible; tables plural snake_case, UUID v7 ids, `timestamptz`, indexed FKs.
- UI text Italian via `t()`; tokens and rules of `docs/frontend-style.md`; 44px targets ≤ 700px; axe clean.
- Backend green: `./mvnw spotless:apply && ./mvnw verify`. Frontend green: `npm run lint && npm run typecheck && npm test && npm run format:check && npm run build`.
- New personal data → `docs/privacy.md` updated in the same branch.

## Review Focus

1. Re-closing / editing a closed list twice must not create duplicate owned rows or fail on the unique index (`owned_volumes` is unique per collection edition + number) — Task 3 `closeTwiceAfterReopenKeepsOneOwnedRow`.
2. Moving a booked row between two closed lists must leave the volume owned (out of one, into the other in the same transaction) — Task 3 `moveBookedBetweenClosedListsStaysOwned`.
3. Deleting the edition from the catalog after rows exist must answer 409, not a 500 from the FK — Task 3 `deleteEditionUsedByRowIs409`.
4. CHF typed by hand must survive an EUR edit in the add/edit form only until the user changes CHF back; proposals must not overwrite what the user typed — Task 6 `editedChfIsNotOverwrittenByEur`.
5. A discount larger than the subtotal after rows are removed must show total 0 and keep the discount value — Task 2 `totalNeverBelowZeroAfterRowsRemoved`.

---

### Task 1: Schema, purchase settings

**Files:**
- Create: `backend/src/main/resources/db/migration/V2026_11_05_1000__purchase_settings.sql`
- Create: `backend/src/main/resources/db/migration/V2026_11_05_1001__create_purchases.sql`
- Modify: `backend/src/main/java/me/luucka/mangashelf/users/AppUser.java` (fields `chfRate` BigDecimal, `discountPercent` int, getters, `setPurchaseSettings(BigDecimal, int)`)
- Create: `backend/src/main/java/me/luucka/mangashelf/purchases/dto/PurchaseSettings.java`
- Create: `backend/src/main/java/me/luucka/mangashelf/purchases/PurchaseController.java`, `PurchaseService.java`
- Test: `backend/src/test/java/me/luucka/mangashelf/purchases/PurchaseSettingsIT.java`

**Interfaces:**
- SQL: `users.chf_rate numeric(4,2) not null default 1.20 check (chf_rate > 0)`, `users.discount_percent int not null default 10 check (discount_percent between 0 and 100)`.
  `purchase_lists(id uuid pk, user_id uuid not null fk users on delete cascade, description text not null, month int not null check 1–12, year int not null check 2000–2100, closed boolean not null default false, discount_cents int null check ≥ 0, points_discount_cents int not null default 0 check ≥ 0, created_at, updated_at timestamptz not null)`, index `ix_purchase_lists_user_id`.
  `purchase_items(id uuid pk, list_id uuid not null fk purchase_lists on delete cascade, edition_id uuid not null fk editions on delete restrict, volume int not null check ≥ 1, release_date date null, price_eur_cents int not null check ≥ 0, price_chf_cents int not null check ≥ 0, booked boolean not null default false, created_at, updated_at)`, indexes `ix_purchase_items_list_id`, `ix_purchase_items_edition_id`. Constraint names follow `V2026_11_04_1000__name_primary_keys.sql` (`pk_<table>`, `fk_<table>_<column>`, `ck_<table>_<column>`).
- `record PurchaseSettings(@NotNull @DecimalMin(value="0.01") @DecimalMax("9.99") @Digits(integer=1, fraction=2) BigDecimal chfRate, @NotNull @Min(0) @Max(100) Integer discountPercent)`.
- `GET /api/v1/account/purchase-settings` → `PurchaseSettings`; `PUT` same body → saved `PurchaseSettings`.
- `PurchaseService.settings(UUID userId)`, `PurchaseService.updateSettings(UUID userId, PurchaseSettings s)`.

- [ ] **Step 1: Failing tests** in `PurchaseSettingsIT` (setup like `CollectionStatsIT`): `defaultsAreRateOneTwentyAndTenPercent` (`$.chfRate` 1.2, `$.discountPercent` 10); `updateIsPersistedPerUser` (Anna PUTs 1.25/15 → Anna GET 1.25/15, Bruno GET 1.2/10); `invalidValuesAre400` (rate 0 → field `chfRate`; percent 101 → field `discountPercent`); `requiresLogin` (401).
- [ ] **Step 2: Run** `./mvnw verify -Dit.test=PurchaseSettingsIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` → FAIL (404).
- [ ] **Step 3: Implement** migrations, `AppUser` fields, DTO, service and controller methods.
- [ ] **Step 4: Run** `./mvnw spotless:apply && ./mvnw verify` → BUILD SUCCESS (all existing tests still green: migrations are additive).
- [ ] **Step 5: Commit** `feat(purchases): schema and per-user purchase settings`

---

### Task 2: Lists and rows, totals

**Files:**
- Create in `backend/src/main/java/me/luucka/mangashelf/purchases/`: `PurchaseList.java`, `PurchaseItem.java` (entities like `CollectionEdition`), `PurchaseListRepository.java`, `PurchaseItemRepository.java`, `Money.java`
- Create in `purchases/dto/`: `ListRequest`, `ListPatch`, `ListSummary`, `ListDetail`, `ItemRequest`, `ItemPatch`, `ItemView`
- Modify: `PurchaseService.java`, `PurchaseController.java`
- Test: `backend/src/test/java/me/luucka/mangashelf/purchases/PurchaseListsIT.java`, `MoneyTest.java`

**Interfaces:**
- `final class Money { static long roundTo10(long numerator, long denominator) }` — nearest 0.10 of `numerator/denominator` cents, halves up, integer only. `static long chf(long eurCents, BigDecimal rate)`; `static long discount(long subtotalCents, int percent)`.
- `record ListRequest(@NotBlank @Size(max=100) String description, @NotNull @Min(1) @Max(12) Integer month, @NotNull @Min(2000) @Max(2100) Integer year)`.
- `record ListPatch(@Size(max=100) String description, @Min(1) @Max(12) Integer month, @Min(2000) @Max(2100) Integer year, @Min(0) Long discountCents, Boolean discountAuto, @Min(0) Long pointsDiscountCents)` — null = unchanged; `discountAuto=true` clears the fixed discount; a non-null `discountCents` sets it fixed. Blank description → 400.
- `record ItemRequest(@NotNull UUID editionId, @NotNull @Min(1) Integer volume, LocalDate releaseDate, @NotNull @Min(0) Long priceEurCents, @NotNull @Min(0) Long priceChfCents, Boolean booked)` (booked null = false).
- `record ItemPatch(UUID editionId, @Min(1) Integer volume, LocalDate releaseDate, Boolean clearReleaseDate, @Min(0) Long priceEurCents, @Min(0) Long priceChfCents, Boolean booked)` — null = unchanged; `clearReleaseDate=true` sets the date to null.
- `record ItemView(String id, String editionId, String seriesId, String seriesTitle, String editionName, int editionVolumeCount, int volume, LocalDate releaseDate, long priceEurCents, long priceChfCents, boolean booked, List<String> alsoIn)` — `alsoIn`: descriptions of the user's *other* lists containing the same edition+volume (filled in Task 3; empty list here).
- `record ListSummary(String id, String description, int month, int year, boolean closed, int itemCount, int unbookedCount, long totalCents)`.
- `record ListDetail(String id, String description, int month, int year, boolean closed, List<ItemView> items, long subtotalCents, long discountCents, boolean discountAuto, long pointsDiscountCents, long totalCents)`; `items` ordered by release date asc nulls last, series title, edition name (case-insensitive), volume.
- Endpoints: `GET /api/v1/purchase-lists` → `List<ListSummary>` (year desc, month desc, created_at desc); `POST` `ListRequest` → 201 `ListDetail`; `GET /{id}` → `ListDetail`; `PATCH /{id}` `ListPatch` → `ListDetail`; `DELETE /{id}` → 204 (409 "Una lista chiusa non si può eliminare" when closed); `POST /{id}/items` `ItemRequest` → 201 `ListDetail`; `PATCH /{id}/items/{itemId}` `ItemPatch` → `ListDetail`; `DELETE /{id}/items/{itemId}` → `ListDetail`.
- Saving a fixed discount or points discount that makes `subtotal − discount − points < 0` → 400 with field `discountCents` / `pointsDiscountCents` ("Lo sconto supera il subtotale").
- Unknown edition → 404 like the rest of the catalog.

- [ ] **Step 1: Failing tests.** `MoneyTest` (plain JUnit): `chf(790, 1.20) == 950`, `chf(995, 1.20) == 1190`, `chf(1250, 1.20) == 1500`, `chf(6400, 1.20) == 7680`; `discount(12610, 10) == 1260`, `discount(16490, 10) == 1650`, `discount(24100, 10) == 2410`; `roundTo10(5, 1) == 10` (half up), `roundTo10(4, 1) == 0`.
  `PurchaseListsIT`: `createAndListOrderedByMonthDesc` (Agosto 8/2026, Ottobre 10/2026, Settembre 9/2026 → order Ottobre, Settembre, Agosto; summary counts and total); `listValidation` (blank description, month 13, year 1999 → 400 with those fields); `rowValidationAndDefaults` (missing price → 400; booked defaults false; release date may be null); `rowsOrderedByDateThenTitle` (null date last); `totalsWithAutomaticDiscount` (two rows with CHF 9500 and 3110 → subtotal 12610, discount 1260, total 11350); `fixedDiscountStaysWhenRowsChange` (PATCH discountCents 500 → add row → discount still 500, `discountAuto` false; PATCH discountAuto true → back to 10%); `pointsDiscountReducesTotal`; `discountAboveSubtotalIs400`; `totalNeverBelowZeroAfterRowsRemoved` (fixed discount 900 on subtotal 950, delete the row → total 0, discount still 900); `deleteOpenListRemovesRows`; `otherUsersListIs404` (GET, PATCH, DELETE, POST item, PATCH item → 404 for Bruno on Anna's list); `itemOfAnotherListIs404` (item id from list B used under list A → 404); `requiresLogin`.
- [ ] **Step 2: Run** `./mvnw verify -Dit.test=PurchaseListsIT -Dtest=MoneyTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3: Implement** entities, repositories, `Money`, service and controller (owner check: load the list by `id` **and** `userId`, else `NotFoundException("Lista non trovata")`). Detail reads rows with one JdbcClient query joining editions and series.
- [ ] **Step 4: Run** `./mvnw spotless:apply && ./mvnw verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** `feat(purchases): lists, rows and totals`

---

### Task 3: Close, reopen, move, collection sync, catalog rules, proposals

**Files:**
- Modify: `purchases/PurchaseService.java`, `PurchaseController.java`, `PurchaseItemRepository.java`; Create `purchases/dto/MoveRequest.java`, `purchases/dto/Proposal.java`
- Modify: `collection/CollectionService.java` (two public methods), `collection/OwnedVolumeRepository.java`
- Modify: `catalog/EditionService.java` (`raiseVolumeCount`, delete and lower-count safeguards), `catalog/Edition.java` if a setter is needed
- Test: `backend/src/test/java/me/luucka/mangashelf/purchases/PurchaseSyncIT.java`

**Interfaces:**
- `CollectionService.addOwned(UUID userId, UUID editionId, int volume)` — ensures the collection edition, inserts the owned volume if absent (idempotent).
- `CollectionService.removeOwned(UUID userId, UUID editionId, int volume)` — deletes it if present; no-op otherwise.
- `EditionService.raiseVolumeCount(UUID editionId, int volume)` — sets `volume_count = volume` when greater; no-op otherwise.
- `PurchaseItemRepository.existsByEditionId(UUID)`, `findMaxVolumeByEditionId(UUID) : Optional<Integer>`, and a query `boolean acquiredElsewhere(UUID userId, UUID editionId, int volume, UUID excludedItemId)` = exists a booked item in a closed list of that user with that edition+volume, other than `excludedItemId` (for list-level actions exclude by list id: `acquiredOutsideList(userId, editionId, volume, listId)`).
- `record MoveRequest(@NotEmpty List<@NotNull UUID> itemIds, @NotNull UUID targetListId, LocalDate releaseDate)` → `POST /api/v1/purchase-lists/{id}/items/move` → `ListDetail` of the source list; every item must belong to `{id}`, target must be another list of the same user (else 404 / 400 field `targetListId` when equal to source).
- `POST /{id}/close`, `POST /{id}/reopen` → `ListDetail`.
- `record Proposal(Integer volume, Long priceEurCents, LocalDate releaseDate)`; `GET /{id}/proposal?editionId=` → volume = 1 + max(user's owned volumes of the edition, volumes in any of the user's rows for it), else 1; EUR = user's most recently created row for that edition; date = most recently created row of this list.
- `ItemView.alsoIn` now filled.
- Sync rule implementation: every state change computes `before` (acquired? edition, volume) and `after` per affected row; on `before && !after` call `removeOwned` unless acquired elsewhere; on `after` call `addOwned`. Catalog bump on add/edit/move-in uses `raiseVolumeCount`.

- [ ] **Step 1: Failing tests** in `PurchaseSyncIT`: `closeMarksOnlyBookedAsOwned`; `reopenMakesThemMissing`; `closeTwiceAfterReopenKeepsOneOwnedRow`; `addBookedRowToClosedListOwnsIt`; `bookingInClosedListOwnsIt`; `unbookingInClosedListMakesMissing`; `deleteBookedRowFromClosedListMakesMissing`; `missingEvenIfTickedByHand` (PUT owned [5] by hand, row vol 5 booked in closed list, delete row → 5 not owned); `stillOwnedViaAnotherClosedList`; `moveBookedOutOfClosedListMakesMissing`; `moveBookedBetweenClosedListsStaysOwned`; `moveAppliesNewDateOnlyWhenGiven`; `editingVolumeOfBookedRowInClosedListMovesOwnership` (5 → 6: 5 missing, 6 owned); `closingAddsEditionToCollection`; `rowAboveVolumeCountRaisesCatalog` (edition 113 vols, row 114 → edition `volumeCount` 114); `deleteEditionUsedByRowIs409`; `loweringVolumeCountBelowARowIs409`; `alsoInListsOtherListsWithSameVolume`; `proposalNextVolumeLastEurAndListDate`; `moveToSameListIs400`; `moveIntoAnotherUsersListIs404`.
- [ ] **Step 2: Run** `./mvnw verify -Dit.test=PurchaseSyncIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3: Implement** as in Interfaces; all actions `@Transactional`.
- [ ] **Step 4: Run** `./mvnw spotless:apply && ./mvnw verify` → BUILD SUCCESS (existing catalog and collection ITs included).
- [ ] **Step 5: Commit** `feat(purchases): close, reopen, move and collection sync`

---

### Task 4: Export, account deletion, privacy

**Files:**
- Modify: `users/dto/AccountExport.java` (add `PurchaseSettings purchaseSettings`, `List<ListDetail> purchaseLists`), `users/AccountService.java`
- Modify: `docs/privacy.md` (processing activities: purchase lists — edition, volume, release date, prices, discounts, booked/closed state; same purpose and legal basis as the collection; retention until deleted by the user or account deletion)
- Test: extend `backend/src/test/java/me/luucka/mangashelf/users/AccountExportIT.java` and `AccountDeletionIT.java`

- [ ] **Step 1: Failing tests:** `exportIncludesPurchases` (`$.purchaseSettings.discountPercent` 10, `$.purchaseLists[0].items[0].volume`); `deletingAccountDeletesPurchases` (count rows in `purchase_lists`/`purchase_items` for the user = 0 after delete).
- [ ] **Step 2: Run** the IT → FAIL on export.
- [ ] **Step 3: Implement** (`PurchaseService.exportAll(UUID userId) : List<ListDetail>`); update `docs/privacy.md`.
- [ ] **Step 4: Run** `./mvnw spotless:apply && ./mvnw verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** `feat(purchases): include purchases in export; privacy doc`

---

### Task 5: Frontend — menu, lists page, settings

**Files:**
- Modify: `frontend/src/api/types.ts` (`PurchaseSettings`, `ListSummary`, `ListDetail`, `ItemView`, `Proposal` mirroring the backend records; cents as `number`)
- Create: `frontend/src/features/purchases/api.ts`, `money.ts`, `PurchasesPage.tsx`, `purchases.test.tsx`
- Create: `frontend/src/features/account/PurchaseSettingsForm.tsx`; Modify `AccountPage.tsx`
- Modify: `frontend/src/components/Layout.tsx` (NavLink `/purchases` between Catalogo and Account), `frontend/src/App.tsx` (routes `/purchases`, `/purchases/:id` lazy like the others), `frontend/src/i18n/it.ts`, `frontend/src/styles.css`

**Interfaces:**
- `money.ts`: `formatChf(cents): string` → `"CHF 9.50"`; `formatEur(cents)` → `"EUR 7.90"` (two decimals, dot, as the user's sheet); `parseAmount(text): number | null` (accepts `7.90`, `7,90`, `7`; returns cents; null when invalid/negative); `proposeChf(eurCents, rate: number): number` = nearest 10 cents, halves up, integer maths on `Math.round(rate * 100)`.
- Query keys: `['purchase-lists']`, `['purchase-list', id]`, `['purchase-settings']`. Mutations invalidate `['purchase-lists']`, the list, and `['collection']` / `['edition']` / `['collection-entry']` where the collection may change (close, reopen, row changes, move).
- i18n keys prefix `purchases.` and `nav.purchases: 'Acquisti'`, `account.purchasesTitle: 'Acquisti'`, `account.chfRate: 'Fattore EUR→CHF'`, `account.discountPercent: 'Sconto predefinito (%)'`.

- [ ] **Step 1: Failing tests** (`purchases.test.tsx`, same fetch-mock pattern as `collection.test.tsx`): `navShowsAcquisti`; `listsShowDescriptionMonthStateCountsAndTotal` ("Manga Ottobre · ottobre 2026", "Aperta", "24 volumi, 2 non prenotati", "CHF 216.90"); `createListGoesToItsPage` (POST body `{description, month, year}` → navigates to `/purchases/<id>`); `createListRequiresDescription` (field error shown); `settingsShowAndSave` (Account page: 1.20 and 10 shown; save PUTs `{chfRate: 1.25, discountPercent: 15}`); `moneyHelpers` (unit: `proposeChf(790,1.2)=950`, `proposeChf(995,1.2)=1190`, `parseAmount('7,90')=790`, `parseAmount('-1')=null`); axe on the lists page.
- [ ] **Step 2: Run** `npx vitest run src/features/purchases` → FAIL.
- [ ] **Step 3: Implement.** Month/year input: `<input type="month">` (value `YYYY-MM`). Lists as `.panel` rows reusing `.edition-row` look; state with `.chip`.
- [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): purchases page and settings`

---

### Task 6: Frontend — list detail: rows, totals

**Files:**
- Create: `frontend/src/features/purchases/PurchaseListPage.tsx`, `ItemForm.tsx`, `EditionPicker.tsx`, `Totals.tsx`
- Modify: `purchases/api.ts`, `purchases.test.tsx`, `i18n/it.ts`, `styles.css`

**Interfaces:**
- `EditionPicker`: search series with the existing `GET /series?q=` (as `CatalogPage`), pick a series, then an edition from `GET /series/{id}` (`SeriesDetail.editions`); emits `{ editionId, label, volumeCount }`.
- `ItemForm` (add and edit): fields edition, volume, date, EUR, CHF; on edition pick in add mode loads `GET /purchase-lists/{id}/proposal?editionId=` and fills volume, EUR, date; CHF = `proposeChf(eur, settings.chfRate)` and recomputed on EUR change **until the user edits CHF**; booked checkbox not in the form (rows start unbooked; toggle on the row).
- Rows grouped by release date (`"20 agosto"` via `Intl.DateTimeFormat('it-IT', {day:'numeric', month:'long'})`), null last under "Data da definire"; volume shows "Unico" when `editionVolumeCount === 1`; booked rows get class `booked` (highlight with `--indigo-wash`); "Già nella lista «X»" note when `alsoIn` non-empty.
- `Totals`: subtotal, discount with **Modifica** (amount input → PATCH `discountCents`) and **Torna al N%** (PATCH `discountAuto: true`, N from settings), points discount input (PATCH `pointsDiscountCents`), total.

- [ ] **Step 1: Failing tests:** `rowsGroupedByDateWithUndatedLast`; `unicoForSingleVolumeEdition`; `addRowUsesProposals` (pick edition → volume 114, EUR 5.90, CHF 7.10 shown; POST body cents); `editedChfIsNotOverwrittenByEur`; `bookedToggleSendsPatch`; `editAndDeleteRow` (delete asks confirmation); `alsoInNoteShown`; `totalsAndDiscountOverride` (Modifica 5.00 → PATCH `discountCents: 500`; Torna al 10% → PATCH `discountAuto: true`); `pointsDiscountSaved`; field error from a 400 (`discountCents`) shown on the input; axe on the detail page.
- [ ] **Step 2: Run** → FAIL. - [ ] **Step 3: Implement.** - [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): purchase list rows and totals`

---

### Task 7: Frontend — move, close/reopen, delete; docs; visual check

**Files:**
- Modify: `PurchaseListPage.tsx`, `purchases/api.ts`, `purchases.test.tsx`, `i18n/it.ts`, `styles.css`
- Create: `frontend/src/features/purchases/MoveDialog.tsx`
- Modify: `README.md` (features), `CHANGELOG.md` (`## [Unreleased]` → `### Added` purchase lists)

**Interfaces:**
- Row checkbox selection; **Sposta selezionate** bar when ≥ 1 selected; per-row **Sposta** opens the same dialog for one row.
- `MoveDialog`: `<dialog>`-style panel (focus trapped, Esc closes, like `ConfirmDelete`) with target select (the user's other lists, "descrizione · mese anno") and optional date → POST `/items/move`.
- **Chiudi lista**: if any row unbooked, `ConfirmDelete`-style confirmation with text "N righe non prenotate: non diventeranno possedute e restano nel totale. Chiudere comunque?"; **Riapri** without confirmation; **Elimina** only when open, with confirmation, then navigate to `/purchases`.

- [ ] **Step 1: Failing tests:** `moveSelectedRows` (two checkboxes → bar → target + date → POST body `{itemIds:[…], targetListId, releaseDate}`); `moveSingleRowKeepsDateWhenEmpty` (body without `releaseDate`); `closeWithUnbookedAsksConfirmation`; `closeWithAllBookedClosesDirectly`; `reopenClosedList`; `deleteOnlyWhenOpen` (no Elimina button when closed; open → confirm → DELETE → back to `/purchases`); `closeInvalidatesCollection` (collection query refetched after close); axe with the dialog open.
- [ ] **Step 2: Run** → FAIL. - [ ] **Step 3: Implement.** - [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): move rows, close and reopen lists` (README/CHANGELOG in the same commit).
- [ ] **Step 6: Visual check** with the dev stack: create a list like the user's October sheet (≈ 6 rows, two dates, one undated, one "Unico"), screenshots desktop 1280×800 and phone 390×844 of lists page, list detail, move dialog, close confirmation, Account section; fix anything off in a follow-up commit; send the screenshots to the user.
