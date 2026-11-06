# Publishers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Editions pick their publisher from an admin-managed list instead of free text; admins manage it on a new general `/admin` page.

**Architecture:** New backend package `publishers` (entity, repository, service, controller, DTOs) on a new `publishers` table; `editions.publisher_id` FK replaces the free-text column (which stays, unused). Every reader of the publisher name joins `publishers`. Frontend gets `features/admin/` (AdminPage + PublishersSection), a nav item, and a datalist-based publisher field in `EditionForm`.

**Tech Stack:** Java 25, Spring Boot 4.1, JPA, JdbcClient, Flyway, PostgreSQL 17, Testcontainers · React 19, TypeScript strict, TanStack Query, Vitest, Testing Library, vitest-axe.

**Spec:** `docs/superpowers/specs/2026-11-06-publishers-design.md`

## Global Constraints

- Publisher name required, ≤ 100 chars, stored stripped; unique on `lower(name)` → duplicate (add or rename) = 400 field `name` "Editore già presente".
- Writes (`POST`, `PATCH`, `DELETE /api/v1/publishers…`) admin only → 403 otherwise; `GET /api/v1/publishers` for every logged-in user, sorted by `lower(name)`.
- Delete only if unused → else 409 "L'editore è usato da alcune edizioni".
- Edition publisher optional; `EditionRequest.publisherId` (nullable UUID); unknown id → 400 field `publisherId` "Editore non trovato". `EditionResponse` has `publisherId` and `publisher` (name).
- Existing editions end with `publisher_id = null`; column `editions.publisher` is kept and never read or written again.
- Migration: new file only, backwards-compatible; `pk_publishers`, `uq_publishers_name` on `lower(name)`, `ck_publishers_name` (length 1–100), `fk_editions_publisher_id` ON DELETE RESTRICT, `ix_editions_publisher_id`.
- Problem Details, Bean Validation, parameterized queries, DTO records, `/api/v1`.
- UI text Italian via `t()`; `docs/frontend-style.md`; 44px targets ≤ 700px; axe clean.
- Backend green: `mvn spotless:apply && mvn verify`. Frontend green: `npm run lint && npm run typecheck && npm test && npm run format:check && npm run build`.

## Review Focus

1. Renaming a publisher must not break the uniqueness check against itself (rename "Panini" → "panini" is allowed: same row) — Task 1 `renameToSameNameDifferentCaseIsAllowed`.
2. Statistics "Per editore" and the collection rows must show the publisher name from the new table, and editions without publisher must still count in Altri — Task 1 `statsAndCollectionUsePublisherTable`.
3. Saving an existing edition through the old form flow must not resurrect or depend on the old text column — Task 1 `oldTextColumnIsIgnored` (insert text in `editions.publisher` via SQL → response `publisher` null).
4. Typing a name with different case in the edition form must resolve to the right publisher id — Task 3 `pickIgnoresCase`.
5. A non-admin must not see the Amministrazione nav item nor reach `/admin` — Task 2 `adminNavOnlyForAdmins`.

---

### Task 1: Backend — publishers and edition link

**Files:**
- Create: `backend/src/main/resources/db/migration/V2026_11_06_1000__create_publishers.sql`
- Create in `backend/src/main/java/me/luucka/mangashelf/publishers/`: `Publisher.java`, `PublisherRepository.java`, `PublisherService.java`, `PublisherController.java`, `dto/PublisherRequest.java`, `dto/PublisherResponse.java`
- Modify: `catalog/Edition.java` (replace the `publisher` field mapping with `publisherId` UUID), `catalog/dto/EditionRequest.java`, `catalog/dto/EditionResponse.java`, `catalog/EditionService.java`, `catalog/SeriesService.java` (detail), `collection/CollectionService.java` (list/entry query and `ownedBy(... "e.publisher")` → publisher name via join)
- Test: `backend/src/test/java/me/luucka/mangashelf/publishers/PublisherIT.java`; update existing ITs that send/assert `publisher` text (catalog, collection, stats, export)

**Interfaces:**
- `record PublisherRequest(@NotBlank @Size(max = 100) String name)`; `record PublisherResponse(String id, String name)`.
- `GET /api/v1/publishers` → `List<PublisherResponse>`; `POST` → 201 + `Location: /api/v1/publishers/{id}` + body; `PATCH /{id}` → body; `DELETE /{id}` → 204.
- `PublisherService.require(UUID) : Publisher` (404 "Editore non trovato" for the publishers API); `EditionService` maps an unknown `publisherId` to 400 field `publisherId` instead.
- `EditionRequest(..., UUID publisherId, ...)` replacing `String publisher`; `EditionResponse(..., String publisherId, String publisher, ...)` (both nullable).
- Statistics keep `StatBar.key` = publisher **name** (names are unique now).

- [ ] **Step 1: Failing tests** in `PublisherIT` (setup like `CollectionStatsIT`, with an admin and a user): `adminAddsRenamesDeletes`; `listIsSortedCaseInsensitiveForEveryUser`; `nonAdminWritesAre403`; `duplicateNameRejectedIgnoringCaseAndSpaces` (add " star comics " when "Star Comics" exists → 400 `name`; rename another publisher to it → 400 `name`); `renameToSameNameDifferentCaseIsAllowed`; `blankOrTooLongNameIs400`; `deleteInUseIs409`; `editionWithPublisherWithoutAndUnknown` (unknown → 400 `publisherId`); `renameShowsInEditionAndCollection`; `statsAndCollectionUsePublisherTable` (two editions, one with publisher P, one without → byPublisher `[P n, null m]`); `oldTextColumnIsIgnored`; `migrationLeavesExistingEditionsWithoutPublisher` (edition created before linking has `publisherId` null).
- [ ] **Step 2: Run** `mvn verify -Dit.test=PublisherIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` → FAIL (404 on /publishers).
- [ ] **Step 3: Implement**; update existing ITs and fixtures that used the text field (they now create a publisher and send its id, or send none).
- [ ] **Step 4: Run** `mvn spotless:apply && mvn verify` → BUILD SUCCESS.
- [ ] **Step 5: Commit** `feat(publishers): managed publisher list linked to editions`

---

### Task 2: Frontend — Amministrazione page

**Files:**
- Modify: `frontend/src/api/types.ts` (`Publisher { id: string; name: string }`, `EditionResponse.publisherId`)
- Create: `frontend/src/features/admin/api.ts` (`usePublishers`, `useCreatePublisher`, `useRenamePublisher`, `useDeletePublisher`; query key `['publishers']`; rename/delete also invalidate `['series-detail']`, `['edition']`, `['collection']`), `AdminPage.tsx`, `PublishersSection.tsx`, `admin.test.tsx`
- Modify: `App.tsx` (route `/admin` under `RequireAuth admin`), `components/Layout.tsx` (NavLink `/admin` "Amministrazione" after Utenti, admin only), `i18n/it.ts`, `styles.css` if needed

- [ ] **Step 1: Failing tests:** `adminNavOnlyForAdmins` (USER: no link and `/admin` behaves like `/admin/users` for a USER; ADMIN: link present); `addPublisher` (POST `{name}`, list updated); `addDuplicateShowsFieldError` (400 `name` → message under the field); `renameAndCancel`; `deleteAsksConfirmation`; `deleteInUseShowsMessage` (409 detail shown); axe on `/admin`.
- [ ] **Step 2: Run** `npx vitest run src/features/admin` → FAIL.
- [ ] **Step 3: Implement** (sections as stacked `.panel`s; rows like `.edition-row`; ConfirmDelete for delete).
- [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): administration page with publishers`

---

### Task 3: Frontend — publisher field in the edition form; docs

**Files:**
- Modify: `frontend/src/features/catalog/EditionForm.tsx` (field `publisher` → datalist of names, sends `publisherId`), `features/catalog/api.ts` (request type), `features/catalog/catalog.test.tsx`, other tests/fixtures using `publisher` in requests, `i18n/it.ts`, `CHANGELOG.md` (Unreleased → Changed/Added)

**Interfaces:**
- Field: `<input id=… list="publisher-options">` + `<datalist id="publisher-options">` with one `<option value={name}>` per publisher (from `usePublishers`, Task 2). On submit: empty → `publisherId: null`; otherwise match `name.trim().toLowerCase()` → id; no match → field error "Scegli un editore dalla lista" and no request. Editing shows the current `edition.publisher`.

- [ ] **Step 1: Failing tests:** `pickPublisherSendsId`; `emptyPublisherSendsNull`; `unknownPublisherShowsErrorAndDoesNotSave`; `pickIgnoresCase`; `editShowsCurrentPublisher`; axe on the form.
- [ ] **Step 2: Run** `npx vitest run src/features/catalog` → FAIL.
- [ ] **Step 3: Implement**; CHANGELOG entry.
- [ ] **Step 4: Run** full frontend checks → green.
- [ ] **Step 5: Commit** `feat(frontend): choose the publisher from the list`
- [ ] **Step 6: Visual check** (controller): `/admin` and the edition form, desktop and phone; screenshots to the user.
