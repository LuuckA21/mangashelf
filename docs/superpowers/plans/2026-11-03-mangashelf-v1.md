# MangaShelf v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App web self-hosted in cui pochi utenti tengono ciascuno la propria collezione manga, volume per
volume, su un catalogo condiviso di serie ed edizioni.

**Architecture:** Monorepo `backend/` (Spring Boot 4, package per funzionalità `users`, `catalog`,
`collection`, `common`, `config`) + `frontend/` (React/Vite) compilato dentro l'immagine del backend e
servito dalla stessa origine. PostgreSQL 17 con Flyway; sessioni in DB con Spring Session JDBC; API REST
`/api/v1` con Problem Details.

**Tech Stack:** Java 25, Spring Boot 4.1.x, Spring Security 7, Spring Session JDBC, Flyway, PostgreSQL 17,
Testcontainers 2, Spotless; React 19, TypeScript strict, Vite, React Router 7, TanStack Query 5, Vitest,
Testing Library, vitest-axe, ESLint + jsx-a11y, Prettier.

**Spec:** `docs/superpowers/specs/2026-11-03-mangashelf-v1-design.md` (leggerla prima di ogni task).
Standard: repository `LuuckA21/project-standards` (guide 01–14, `templates/`).
Materiale di riferimento dal vecchio progetto: `docs/superpowers/plans/reference/` (stile, componenti,
config). Va cancellato nell'ultimo task.

## Global Constraints

- Java package `me.luucka.mangashelf`, groupId `me.luucka`, artifactId `mangashelf`, versione `0.1.0-SNAPSHOT`.
- Tutte le API sotto `/api/v1`; errori `application/problem+json`; DTO `record`; ID serializzati come stringhe.
- Tabelle plurali snake_case, `id uuid` v7 generato dall'app (`@UuidGenerator(style = VERSION_7)`),
  `created_at`/`updated_at timestamptz`, vincoli nominati `pk_`/`fk_`/`uq_`/`ix_`/`ck_`, indice su ogni FK.
- Migrazioni `backend/src/main/resources/db/migration/V2026_11_03_HHMM__<descrizione>.sql`; mai modificarne
  una già committata.
- `ddl-auto: validate`, `open-in-view: false`, `hibernate.jdbc.time_zone: UTC`, `max-page-size: 100`,
  paginazione `VIA_DTO` (`{content, page:{size,number,totalElements,totalPages}}`).
- Enum DB: `role` ∈ `ADMIN|USER`; `type` ∈ `REGULAR|VARIANT|JAPANESE`; `status` ∈ `ONGOING|COMPLETED`.
- Username 3–32 caratteri `[a-zA-Z0-9._-]`, unico senza distinzione di maiuscole. Password 12–64 caratteri.
  `volume_count` 1–9999. Copertina: solo JPEG/PNG, max 5 MB, salvata come JPEG ≤ 480×720.
- Sessione: 14 giorni di inattività; cookie `HttpOnly`, `SameSite=Lax`, `Secure` fuori dal profilo `dev`; CSRF attivo.
- Messaggio di login fallito: esattamente `Credenziali non valide`.
- Frontend: percorsi in inglese; testi solo italiani, tutti via `t('chiave')` da `src/i18n/it.ts`; stili solo
  in `src/styles.css` secondo `docs/frontend-style.md`; Prettier `semi: false`, `singleQuote: true`.
- Comandi di verifica: backend `./mvnw verify` (in `backend/`); frontend `npm run lint && npm run typecheck && npm test && npm run build` (in `frontend/`).
- Commit Conventional Commits in inglese, sul branch `claude/great-hopper-o2dsm6`.

## Review Focus

1. **Username con maiuscole diverse**: creato `Luca`, login con `luca` deve riuscire e `POST /users` con
   `LUCA` deve dare 409. → test in Task 2 e Task 4.
2. **Titoli con spazi o maiuscole diverse**: `" one piece "` con `One Piece` già presente → 409 (titoli e nomi
   edizione vengono `strip()`-ati prima di salvare). → test in Task 5.
3. **Limite di `volumeCount`**: un utente possiede il volume 114; abbassare a 114 riesce, a 113 → 409. → test in Task 6.
4. **Edizione desiderio con 0 volumi**: un'edizione in collezione senza volumi compare in `GET /collection`
   con `owned: []` e passa il filtro `incomplete=true`. → test in Task 6.
5. **PNG con trasparenza**: la ricodifica in JPEG non deve fallire né diventare nera; i pixel trasparenti
   diventano `#f1efe8` (carta). → test in Task 7.

---

## Esecuzione in questo sandbox (non è codice di prodotto)

Prima del Task 1, se `java -version` non è 25 o `docker ps` fallisce:

```bash
apt-get install -y openjdk-25-jdk-headless && update-alternatives --set java /usr/lib/jvm/java-25-openjdk-amd64/bin/java
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
(dockerd > /tmp/dockerd.log 2>&1 &) ; sleep 5 ; docker ps
```

start.spring.io non è raggiungibile: il backend si crea a mano (Task 1), non con `new-project.sh --spring`.

---

### Task 1: Scheletro del repository e del backend

**Files:**
- Create (da `project-standards/templates`, con `new-project.sh` senza flag:
  `../project-standards/new-project.sh /home/user/mangashelf LuuckA21/mangashelf`): `README.md`,
  `CHANGELOG.md`, `CLAUDE.md`, `.gitignore`, `.gitattributes`, `.editorconfig`, `.dockerignore`, `.nvmrc`,
  `.claude/settings.json`, `.github/workflows/{ci,release,deploy}.yml`, `.github/dependabot.yml`,
  `.github/pull_request_template.md`, `backend/.env.example`, `backend/Dockerfile`, `backend/compose.yaml`,
  `backend/src/main/resources/application.yml`, `docs/privacy.md`
- Create: `.sdkmanrc` (`java=25-tem`), `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/`,
  `backend/src/main/java/me/luucka/mangashelf/Application.java`,
  `backend/src/main/java/me/luucka/mangashelf/config/{AppProperties,SecurityConfig}.java`,
  `backend/src/main/resources/db/migration/V2026_11_03_1000__create_users.sql`
- Test: `backend/src/test/java/me/luucka/mangashelf/ApplicationIT.java`,
  `backend/src/test/java/me/luucka/mangashelf/TestcontainersConfig.java`

**Interfaces:**
- Produces: `TestcontainersConfig` (`@TestConfiguration` con `@Bean @ServiceConnection PostgreSQLContainer postgres()` su `postgres:17`), importata da ogni test di integrazione con `@Import(TestcontainersConfig.class)`.
- Produces: `AppProperties(String baseUrl, String frontendOrigin, Path uploadsDir, InitialAdmin initialAdmin)` con `record InitialAdmin(String username, String password)`; prefisso `app`.

- [ ] **Step 1: Copiare i template** con il comando sopra; controllare che `git status` mostri i file elencati e nessun file sovrascritto.

- [ ] **Step 2: Scrivere `backend/pom.xml`** partendo da `reference/pom.xml`: parent `spring-boot-starter-parent` 4.1.x, `<java.version>25</java.version>`, testcontainers-bom 2.x.
  Dipendenze: `spring-boot-starter-web`, `-security`, `-validation`, `-data-jpa`, `-flyway`, `-actuator`,
  `flyway-database-postgresql`, `postgresql`, `spring-session-jdbc`, `springdoc-openapi-starter-webmvc-ui`
  (major compatibile con Boot 4), `spring-boot-docker-compose` (optional, solo dev);
  test: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`,
  `testcontainers-postgresql`, `testcontainers-junit-jupiter`.
  Niente Lombok, mail, caffeine, httpclient5. Plugin: `spotless-maven-plugin` (googleJavaFormat + removeUnusedImports, goal `check`),
  compiler con `-Xlint:all`, `maven-failsafe-plugin` per i `*IT`. Poi `mvn -N wrapper:wrapper` in `backend/`.

- [ ] **Step 3: Adattare `application.yml`** (template): aggiungere
  `server.servlet.session.timeout: 14d`, `spring.session.jdbc.initialize-schema: never`,
  `app.uploads-dir: ${APP_UPLOADS_DIR:/app/uploads}`, `app.initial-admin.username: ${ADMIN_USERNAME:}`,
  `app.initial-admin.password: ${ADMIN_INITIAL_PASSWORD:}`; nel profilo `dev` `app.uploads-dir: ./uploads`.
  In `backend/.env.example` aggiungere `ADMIN_USERNAME`, `ADMIN_INITIAL_PASSWORD`, `APP_UPLOADS_DIR` con commento;
  in `.gitignore` aggiungere `backend/uploads/`. Rimuovere `SENTRY_DSN` (fuori dalla v1). `compose.yaml`: utente/DB `mangashelf`.

- [ ] **Step 4: Scrivere il test che fallisce** `ApplicationIT.healthIsUp`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
  GET `/actuator/health` → 200 e body contiene `"UP"`; GET `/api/v1/anything` senza login → 401.

- [ ] **Step 5: Eseguire** `./mvnw verify` → FAIL (manca `Application`).

- [ ] **Step 6: Creare `Application`** (`@SpringBootApplication @ConfigurationPropertiesScan`) e `AppProperties`.
  Una prima migrazione `V2026_11_03_1000__create_users.sql` serve già qui perché Flyway parta: tabella `users`
  come da spec (vedi Task 2 per le colonne) — crearla ora con tutte le colonne definitive.
  Un `SecurityConfig` minimo in `config/` con `/api/**` autenticato, `HttpStatusEntryPoint(UNAUTHORIZED)`,
  `anyRequest().permitAll()`.

- [ ] **Step 7: Eseguire** `./mvnw spotless:apply && ./mvnw verify` → PASS.

- [ ] **Step 8: Completare README e CLAUDE.md**: README dal template con nome, descrizione di una riga (dalla
  spec), "Getting started" della guida 10; in `CLAUDE.md` la sezione *Project specifics* è già corretta.

- [ ] **Step 9: Commit** `chore: scaffold project from project-standards`.

---

### Task 2: Errori comuni, login, sessioni e admin iniziale

**Files:**
- Create: `common/NotFoundException.java`, `common/ConflictException.java`, `common/ApiExceptionHandler.java`
- Create: `users/AppUser.java`, `users/Role.java`, `users/AppUserRepository.java`, `users/AppUserPrincipal.java`,
  `users/AppUserDetailsService.java`, `users/AuthController.java`, `users/dto/LoginRequest.java`,
  `users/dto/MeResponse.java`, `users/InitialAdminCreator.java`
- Create: `db/migration/V2026_11_03_1001__create_spring_session.sql` (schema PostgreSQL ufficiale di Spring Session JDBC)
- Modify: `config/SecurityConfig.java`
- Test: `users/AuthIT.java`, `users/InitialAdminCreatorIT.java`

(percorsi Java relativi a `backend/src/main/java/me/luucka/mangashelf/` e al corrispondente `src/test/java/...`)

**Interfaces:**
- Produces: `NotFoundException(String detail)` → 404, `ConflictException(String detail)` → 409; `ApiExceptionHandler extends ResponseEntityExceptionHandler` mappa anche `ObjectOptimisticLockingFailureException` → 409, `DataIntegrityViolationException` su vincoli `uq_*` → 409, `AccessDeniedException` → 403, `MaxUploadSizeExceededException` → 413; validazione → 400 con proprietà `errors: [{field, message}]`.
- Produces: `AppUser` (id, username, passwordHash, role, mustChangePassword, createdAt, updatedAt); `AppUserRepository.findByUsernameIgnoreCase(String): Optional<AppUser>`, `existsByUsernameIgnoreCase(String)`, `countByRole(Role)`.
- Produces: `AppUserPrincipal implements UserDetails` con `UUID id()`, `Role role()`, `boolean mustChangePassword()`, `boolean isAdmin()`; i controller lo ricevono con `@AuthenticationPrincipal AppUserPrincipal me`.
- Produces: `MeResponse(String id, String username, Role role, boolean mustChangePassword)`.

- [ ] **Step 1: Test che falliscono** in `AuthIT` (MockMvc + `@Import(TestcontainersConfig.class)`, utenti creati via repository + `PasswordEncoder`):
  - `meWithoutLoginIs401AndSetsXsrfCookie`: GET `/api/v1/auth/me` → 401, risposta con cookie `XSRF-TOKEN`.
  - `loginWithValidCredentialsReturnsMe`: POST `/api/v1/auth/login` `{username:"Luca",password:"correct-horse-1"}` con CSRF → 200, body `username = "Luca"`; poi GET `/auth/me` con la stessa sessione → 200.
  - `loginIsCaseInsensitiveOnUsername`: login con `"luca"` → 200 (Review Focus 1).
  - `wrongPasswordAndUnknownUserGiveSameMessage`: entrambi → 401 con `detail = "Credenziali non valide"`.
  - `loginWithoutCsrfIs403`.
  - `logoutEndsSession`: POST `/auth/logout` → 204, poi `/auth/me` → 401.
  - `validationErrorIsProblemDetail`: login con `username` vuoto → 400, `content-type application/problem+json`, `errors[0].field = "username"`.

- [ ] **Step 2: Eseguire** `./mvnw verify -Dit.test=AuthIT` → FAIL.

- [ ] **Step 3: Implementare.** `SecurityConfig`: `csrf(c -> c.spa())` e il cookie `XSRF-TOKEN` deve essere emesso anche sulle GET (se `spa()` non lo fa da solo, aggiungere il filtro che carica il token, come nella reference Spring Security *CSRF → Single-Page Applications*); `/api/v1/auth/login` permesso, `/api/**` autenticato; `AuthenticationManager` bean da `AppUserDetailsService` + `PasswordEncoderFactories.createDelegatingPasswordEncoder()`; header CSP `default-src 'self'; img-src 'self' blob:`, `frameOptions deny`, `referrerPolicy STRICT_ORIGIN_WHEN_CROSS_ORIGIN`. `AuthController`: login autentica, cambia id di sessione e salva il `SecurityContext` con `HttpSessionSecurityContextRepository`; `BadCredentialsException` → 401 `Credenziali non valide`.

- [ ] **Step 4: Eseguire** → PASS.

- [ ] **Step 5: Test che fallisce** `InitialAdminCreatorIT`:
  - `createsAdminWhenNoUsers`: con `app.initial-admin.username=admin` e `password=initial-pass-123`, tabella vuota → dopo l'avvio esiste `admin` con ruolo `ADMIN` e `mustChangePassword = true`.
  - `doesNothingWhenUsersExist`: rieseguire `InitialAdminCreator.run()` con un utente già presente → nessun nuovo utente.
  - `doesNothingWhenNotConfigured`: proprietà vuote → nessun utente.

- [ ] **Step 6: Implementare** `InitialAdminCreator implements ApplicationRunner`; con password configurata ma più corta di 12 caratteri fallisce all'avvio con messaggio chiaro. Eseguire → PASS.

- [ ] **Step 7: Commit** `feat(users): session login, problem details and initial admin`.

---

### Task 3: Cambio password, blocco password temporanea, chiusura sessioni

**Files:**
- Create: `users/UserSessions.java`, `users/MustChangePasswordFilter.java`, `users/dto/PasswordChangeRequest.java`
- Modify: `users/AuthController.java`, `config/SecurityConfig.java`
- Test: `users/PasswordChangeIT.java`

**Interfaces:**
- Consumes: `AppUserPrincipal`, `AppUserRepository` (Task 2).
- Produces: `UserSessions.closeAll(String username)` — cancella tutte le sessioni indicizzate per quel principal (`FindByIndexNameSessionRepository`).
- Produces: `PasswordChangeRequest(@NotBlank String currentPassword, @Size(min = 12, max = 64) String newPassword)`.

- [ ] **Step 1: Test che falliscono** in `PasswordChangeIT`:
  - `temporaryPasswordBlocksOtherApis`: utente con `mustChangePassword = true` loggato → GET `/api/v1/series` → 403; GET `/auth/me` → 200.
  - `changePasswordClearsFlagAndClosesAllSessions`: due sessioni dello stesso utente; POST `/auth/password` → 204; entrambe le sessioni su `/auth/me` → 401; login con la nuova password → `mustChangePassword = false`.
  - `wrongCurrentPasswordIs400` con `errors[0].field = "currentPassword"`.
  - `tooShortNewPasswordIs400` (11 caratteri) e `tooLongNewPasswordIs400` (65).

- [ ] **Step 2: Eseguire** → FAIL.

- [ ] **Step 3: Implementare** `POST /api/v1/auth/password` e `MustChangePasswordFilter` (dopo l'autenticazione; consente solo `GET /api/v1/auth/me`, `POST /api/v1/auth/password`, `POST /api/v1/auth/logout`; altrimenti 403 Problem Details).

- [ ] **Step 4: Eseguire** `./mvnw verify` → PASS.

- [ ] **Step 5: Commit** `feat(users): password change with forced first change`.

---

### Task 4: Gestione utenti (admin) ed eliminazione del proprio account

**Files:**
- Create: `users/AdminUserController.java`, `users/AdminUserService.java`, `users/AccountController.java`,
  `users/AccountService.java`, `users/dto/{CreateUserRequest,ResetPasswordRequest,UpdateUserRequest,UserResponse,DeleteAccountRequest}.java`
- Test: `users/AdminUserIT.java`, `users/AccountDeletionIT.java`

**Interfaces:**
- Consumes: `UserSessions.closeAll` (Task 3).
- Produces: `CreateUserRequest(@Pattern("[a-zA-Z0-9._-]{3,32}") String username, @Size(min=12,max=64) String temporaryPassword, @NotNull Role role)`; `UserResponse(String id, String username, Role role, boolean mustChangePassword, Instant createdAt)`.
- Produces: `AccountService.delete(UUID userId, String password)` (usato da `DELETE /api/v1/account`).

- [ ] **Step 1: Test che falliscono** in `AdminUserIT`:
  - `nonAdminGets403` su `GET /api/v1/users`.
  - `createUserReturns201WithLocationAndForcesPasswordChange`.
  - `duplicateUsernameIgnoringCaseIs409`: esiste `Luca`, crea `LUCA` → 409 (Review Focus 1).
  - `invalidUsernameIs400` (`"a b"`, `"ab"`).
  - `resetPasswordSetsFlagAndClosesSessions`: sessione dell'utente → 401 dopo il reset.
  - `changeRoleClosesSessions`; `cannotChangeOwnRoleIs409`; `cannotDemoteLastAdminIs409`.
  - `deleteUserCascadesAndClosesSessions`: sessione dell'utente eliminato → 401; `cannotDeleteSelfIs409`.

- [ ] **Step 2: Test che falliscono** in `AccountDeletionIT`:
  - `deleteAccountWithWrongPasswordIs400`.
  - `deleteAccountRemovesUserAndLogsOut` → 204, poi `/auth/me` → 401.
  - `lastAdminCannotDeleteAccountIs409`.

- [ ] **Step 3: Eseguire** → FAIL. **Step 4: Implementare** gli endpoint della spec (§ API → Utenti, `DELETE /account`); controlli "ultimo admin" e "se stesso" nel service. **Step 5: Eseguire** `./mvnw verify` → PASS.

- [ ] **Step 6: Commit** `feat(users): admin user management and account deletion`.

---

### Task 5: Catalogo — serie ed edizioni

**Files:**
- Create: `db/migration/V2026_11_03_1100__create_series_and_editions.sql`
- Create: `catalog/{Series,Edition,EditionType,EditionStatus,SeriesRepository,EditionRepository,SeriesService,EditionService,SeriesController,EditionController}.java`,
  `catalog/dto/{SeriesRequest,SeriesSummary,SeriesDetail,EditionRequest,EditionResponse}.java`
- Test: `catalog/CatalogIT.java`

**Interfaces:**
- Produces: `SeriesRequest(@NotBlank @Size(max=200) String title)`; `SeriesSummary(String id, String title, long editionCount)`; `SeriesDetail(String id, String title, List<EditionResponse> editions)`.
- Produces: `EditionRequest(@NotBlank @Size(max=200) String name, @Size(max=100) String publisher, @NotNull EditionType type, @NotNull EditionStatus status, @Min(1) @Max(9999) int volumeCount, Long version)` (`version` obbligatorio solo su PUT → 400 se manca).
- Produces: `EditionResponse(String id, String seriesId, String seriesTitle, String name, String publisher, EditionType type, EditionStatus status, int volumeCount, boolean hasCover, boolean canEditCover, long version, long collectionCount, boolean inMyCollection)`. In questo task `collectionCount = 0` e `inMyCollection = false`; il Task 6 li calcola.
- Produces: `EditionService.get(UUID id, AppUserPrincipal me): EditionResponse`, `Edition EditionService.require(UUID id)` (404 se assente) — usati da Task 6 e 7.

- [ ] **Step 1: Test che falliscono** in `CatalogIT`:
  - `anyUserCreatesSeriesAndEdition`: POST `/series` `{title:"One Piece"}` → 201 + `Location`; POST `/series/{id}/editions` `{name:"Edizione blu", type:"REGULAR", status:"ONGOING", volumeCount:114}` → 201, `publisher` null.
  - `duplicateTitleTrimmedAndIgnoringCaseIs409`: `" one piece "` → 409 (Review Focus 2); stesso per il nome edizione nella stessa serie; stesso nome in un'altra serie → 201.
  - `listSeriesSearchesAndPaginates`: `GET /series?q=pie` trova One Piece con `editionCount = 1`; `size=101` → pagina da 100.
  - `seriesDetailListsEditions`.
  - `nonAdminCannotUpdateOrDelete`: PUT/DELETE su serie ed edizione → 403.
  - `adminUpdatesEditionWithVersion`: PUT con `version` corrente → 200 e `version` incrementata; PUT con la versione vecchia → 409.
  - `volumeCountOutOfRangeIs400` (0 e 10000); `unknownTypeIs400`.
  - `deleteSeriesWithEditionsIs409`; `deleteEmptySeriesIs204`; `unknownIdIs404`.

- [ ] **Step 2: Eseguire** → FAIL. **Step 3: Implementare** migrazione (spec § Dati, `series` ed `editions`), entità con `@Version` su `Edition`, service con `strip()` su titoli/nomi/editore (editore vuoto → null) e controlli admin. **Step 4: Eseguire** `./mvnw verify` → PASS.

- [ ] **Step 5: Commit** `feat(catalog): shared series and editions`.

---

### Task 6: Collezione, volumi posseduti ed export dell'account

**Files:**
- Create: `db/migration/V2026_11_03_1200__create_collection.sql`
- Create: `collection/{CollectionEdition,OwnedVolume,CollectionEditionRepository,OwnedVolumeRepository,CollectionService,CollectionController}.java`,
  `collection/dto/{CollectionEntry,OwnedVolumesRequest}.java`
- Create: `users/dto/AccountExport.java`; Modify: `users/AccountController.java` (`GET /account/export`)
- Modify: `catalog/EditionService.java` (guard su `volumeCount`, `collectionCount`, `inMyCollection`), `catalog/SeriesService.java` (`inMyCollection` nel dettaglio)
- Test: `collection/CollectionIT.java`, `users/AccountExportIT.java`

**Interfaces:**
- Consumes: `EditionService.require` (Task 5).
- Produces: `OwnedVolumeRepository.findMaxNumberByEditionId(UUID editionId): Optional<Integer>` (massimo tra tutti gli utenti) e `CollectionEditionRepository.countByEditionId(UUID)`, `existsByUserIdAndEditionId(UUID, UUID)` — usati da `EditionService`.
- Produces: `CollectionEntry(String editionId, String seriesId, String seriesTitle, String editionName, String publisher, EditionType type, EditionStatus status, int volumeCount, boolean hasCover, List<Integer> owned)` (`owned` ordinato crescente).
- Produces: `OwnedVolumesRequest(@NotNull List<@NotNull Integer> owned)`; `CollectionService.listAll(UUID userId): List<CollectionEntry>` per l'export.
- Produces: `AccountExport(String username, Role role, Instant createdAt, List<CollectionEntry> collection)`.

- [ ] **Step 1: Test che falliscono** in `CollectionIT`:
  - `addEditionIsIdempotent`: PUT due volte → 200, una sola voce.
  - `setOwnedVolumesReplacesSet`: PUT `/volumes` `{owned:[1,2,5]}` poi `{owned:[2,3]}` → `GET /collection` ha `owned = [2,3]`.
  - `settingVolumesAddsEditionToCollection` se non c'era.
  - `outOfRangeOrDuplicateVolumesIs400`: `[0]`, `[115]` con `volumeCount 114`, `[3,3]` → 400 con `errors[0].field = "owned"`.
  - `wishWithZeroVolumesIsListedAndIncomplete`: edizione aggiunta senza volumi → in `GET /collection` con `owned = []`; presente con `incomplete=true` (Review Focus 4); un'edizione con tutti i volumi non compare con `incomplete=true`.
  - `filtersByTextTypeAndStatus`: `q` cerca su titolo serie e nome edizione.
  - `collectionsAreIsolated`: l'utente B non vede in `GET /collection` le edizioni di A; `DELETE /collection/editions/{id}` di B non tocca A.
  - `removeEditionDeletesItsVolumes` → 204.
  - `lowerVolumeCountBelowOwnedIs409`: A possiede 114; admin PUT `volumeCount 114` → 200, `113` → 409 (Review Focus 3).
  - `editionShowsCollectionCountAndMine`: `collectionCount = 2` con A e B; `inMyCollection` true per A.
  - `deletingEditionRemovesItFromCollections`; `deletingUserRemovesTheirCollection`.

- [ ] **Step 2: Test che fallisce** in `AccountExportIT.exportsProfileAndCollection`: GET `/account/export` → 200, JSON con `username`, `role`, `createdAt`, `collection[0].owned`.

- [ ] **Step 3: Eseguire** → FAIL. **Step 4: Implementare** migrazione (spec § Dati, `collection_editions` e `owned_volumes`, cascade), service e controller (§ API → Collezione). La lista paginata con i filtri `incomplete`/`q` va bene in SQL con `JdbcClient`. **Step 5: Eseguire** `./mvnw verify` → PASS.

- [ ] **Step 6: Commit** `feat(collection): per-user owned volumes and data export`.

---

### Task 7: Copertine

**Files:**
- Create: `catalog/CoverImages.java`, `catalog/CoverStorage.java`, `catalog/InvalidImageException.java`
- Modify: `catalog/EditionController.java`, `catalog/EditionService.java`, `common/ApiExceptionHandler.java`, `application.yml` (`spring.servlet.multipart.max-file-size: 5MB`, `max-request-size: 6MB`)
- Test: `catalog/CoverImagesTest.java` (unit), `catalog/CoverIT.java`

**Interfaces:**
- Produces: `CoverImages.toJpeg(byte[] input): byte[]` — legge con `ImageIO` (solo formati `jpeg`/`png`, altrimenti `InvalidImageException` → 400), ridimensiona entro 480×720 mantenendo le proporzioni (mai ingrandire), disegna su sfondo `#f1efe8` in `TYPE_INT_RGB`, scrive JPEG qualità 0.85.
- Produces: `CoverStorage.save(byte[] jpeg): String fileName` (`<uuid>.jpg` in `app.uploads-dir`), `read(String fileName): byte[]`, `delete(String fileName)` (ignora file assente).

- [ ] **Step 1: Test unit che falliscono** in `CoverImagesTest`:
  - `shrinksLargeImageKeepingRatio`: PNG 1200×1800 → JPEG 480×720.
  - `doesNotUpscale`: 100×150 → 100×150.
  - `transparentPngBecomesPaper`: PNG ARGB tutto trasparente → pixel (0,0) del JPEG ≈ `#f1efe8` (tolleranza ±3 per canale) (Review Focus 5).
  - `stripsExif`: JPEG con segmento APP1 Exif in input → output senza la sequenza di byte `Exif\0\0`.
  - `rejectsNonImage`: `"hello".getBytes()` → `InvalidImageException`; GIF valida → `InvalidImageException`.

- [ ] **Step 2: Test di integrazione che falliscono** in `CoverIT`:
  - `creatorUploadsAndEveryoneReads`: PUT multipart `file` → 204; GET `/editions/{id}/cover` da un altro utente → 200 `image/jpeg`, header `X-Content-Type-Options: nosniff`, `Cache-Control` con `private`; `EditionResponse.hasCover = true`.
  - `otherNonAdminUserCannotUploadIs403`; `adminCan`.
  - `tooLargeIs413` (file > 5 MB).
  - `replacingDeletesOldFile` e `deletingEditionDeletesFile` (directory temporanea `@TempDir` come `app.uploads-dir`).
  - `noCoverIs404`; `deleteCoverIs204ThenGetIs404` (autore o admin).

- [ ] **Step 3: Eseguire** → FAIL. **Step 4: Implementare** (eliminazione del file dopo il commit della transazione). **Step 5: Eseguire** `./mvnw verify` → PASS.

- [ ] **Step 6: Commit** `feat(catalog): edition covers re-encoded as JPEG`.

---

### Task 8: Frontend — scheletro, stile, i18n e client API

**Files:**
- Create: `frontend/` con `npm create vite@latest frontend -- --template react-ts`, poi `package.json` con gli script
  `dev`, `build` (`tsc -b && vite build`), `lint` (`eslint . --max-warnings 0`), `typecheck` (`tsc -b --noEmit`),
  `test` (`vitest run`), `format`, `format:check`
- Create: `frontend/.prettierrc.json` (da reference), `frontend/eslint.config.js` (da reference + `eslint-plugin-jsx-a11y` recommended),
  `frontend/vitest.config.ts` (da reference), `frontend/src/test/setup.ts` (jest-dom + `vitest-axe/extend-expect`),
  `frontend/vite.config.ts` (proxy `/api` → `http://localhost:8080`)
- Create: `frontend/src/styles.css`, `frontend/src/styles.test.ts`, `docs/frontend-style.md`
- Create: `frontend/src/i18n/it.ts`, `frontend/src/i18n/t.ts`, `frontend/src/i18n/t.test.ts`
- Create: `frontend/src/api/client.ts`, `frontend/src/api/client.test.ts`, `frontend/src/api/types.ts`
- Modify: `frontend/index.html` (`<html lang="it">`, titolo `MangaShelf`)

**Interfaces:**
- Produces: `t(key: TranslationKey, params?: Record<string, string | number>): string` con `TranslationKey = keyof typeof it`; sostituisce `{nome}` nei testi.
- Produces: `api<T>(path: string, init?: RequestInit): Promise<T>` e `class ApiError { status: number; detail: string; errors: { field: string; message: string }[] }` — come l'esempio della guida 11, con `path` relativo a `/api/v1`; un 401 da qualunque path tranne `/auth/me` e `/auth/login` porta a `/login`; corpo `FormData` senza `Content-Type`.
- Produces: in `types.ts` i tipi TS di `MeResponse`, `UserResponse`, `SeriesSummary`, `SeriesDetail`, `EditionResponse`, `CollectionEntry`, `Page<T> = { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number } }`, `EditionType`, `EditionStatus`, `Role` (stessi nomi e campi dei DTO dei Task 2–7).

- [ ] **Step 1: Dipendenze**: `react-router`, `@tanstack/react-query`; dev: `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `vitest-axe`, `eslint-plugin-jsx-a11y`, `prettier`. `npm run lint` e `npm run typecheck` → PASS sul template.

- [ ] **Step 2: Stile.** `docs/frontend-style.md` = `reference/frontend-style.original.md` con le modifiche elencate in spec § Frontend → Stile (link a file che esistono nel nuovo repo). `styles.css` = `reference/styles.css` meno le sezioni *AniList search results*, *purchase list*, *purchase status*, *suggestions*, *cover picker* se inutilizzata, la sezione 2FA e le regole `.tile.future`, `.reserved`, `.bought`, `.reserve-toggle`, `.stats-*`. `styles.test.ts` = reference con le sole asserzioni che restano valide (contrasti, 44px su controlli e `.link-button`, nav che va a capo, `data-label`).

- [ ] **Step 3: Test che falliscono**:
  - `t.test.ts`: `t('collection.title')` restituisce la stringa italiana; `t('shelf.volumeOwned', { n: 12 })` → `"Volume 12, posseduto"`.
  - `client.test.ts` (fetch mockato): `sendsXsrfHeaderOnPost` (cookie `XSRF-TOKEN=abc` → header `X-XSRF-TOKEN: abc`, nessun header su GET); `throwsApiErrorFromProblemDetails` (400 con `errors` → `ApiError.errors[0].field`); `redirectsToLoginOn401` (`window.location.assign('/login')`), ma non per `/auth/me`; `returnsUndefinedOn204`.

- [ ] **Step 4: Eseguire** `npm test` → FAIL. **Step 5: Implementare.** **Step 6: Eseguire** tutti i controlli del frontend → PASS.

- [ ] **Step 7: Commit** `feat(frontend): scaffold with style, i18n and api client`.

---

### Task 9: Frontend — routing, layout e autenticazione

**Files:**
- Create: `frontend/src/main.tsx`, `frontend/src/App.tsx` (router con `React.lazy` per ogni pagina), `frontend/src/components/Layout.tsx`
- Create: `frontend/src/features/auth/{useMe.ts,RequireAuth.tsx,LoginPage.tsx,ChangePasswordPage.tsx,auth.test.tsx}`

**Interfaces:**
- Consumes: `api`, `ApiError`, `t`, `MeResponse` (Task 8).
- Produces: `useMe(): UseQueryResult<MeResponse | null>` (query key `['me']`; 401 → `null`); `<RequireAuth admin?>` che rimanda a `/login` senza utente, a `/change-password` con `mustChangePassword`, e mostra 404 a un non admin su rotte admin.
- Produces: `Layout` con topbar (brand, nav: Collezione `/`, Catalogo `/catalog`, Utenti `/admin/users` solo admin, Account `/account`, Esci), `<main>`, footer con link `/privacy`.

- [ ] **Step 1: Test che falliscono** in `auth.test.tsx` (fetch mockato, `MemoryRouter`):
  - `redirectsAnonymousUserToLogin`.
  - `loginShowsInvalidCredentialsMessage`: risposta 401 `detail` → testo `Credenziali non valide` visibile.
  - `temporaryPasswordGoesToChangePassword`: dopo il login `mustChangePassword: true` → si apre `/change-password`.
  - `changePasswordShowsFieldError`: 400 con `errors[{field:"newPassword"}]` → messaggio accanto al campo, collegato con `aria-describedby`.
  - `loginPageHasNoAxeViolations`.

- [ ] **Step 2: Eseguire** → FAIL. **Step 3: Implementare** (dopo il cambio password la sessione è chiusa: invalidare `['me']` e andare a `/login` con un messaggio `.success`). **Step 4: Eseguire** i controlli del frontend → PASS.

- [ ] **Step 5: Commit** `feat(frontend): routing, layout and login`.

---

### Task 10: Frontend — collezione, pagina edizione e scaffale

**Files:**
- Create: `frontend/src/components/{Shelf.tsx,Shelf.test.tsx,ConfirmDelete.tsx}`
- Create: `frontend/src/features/collection/{api.ts,formatMissing.ts,formatMissing.test.ts,CollectionPage.tsx,EditionPage.tsx,collection.test.tsx}`

**Interfaces:**
- Consumes: `useMe`, `RequireAuth`, `Layout` (Task 9); tipi `CollectionEntry`, `EditionResponse`.
- Produces: `formatMissing(owned: number[], volumeCount: number): string` — es. `([1,2,4,5,6,10], 10)` → `"3, 7–9"`; stringa vuota se non manca nulla.
- Produces: `<Shelf volumeCount owned onChange(owned: number[]) busy?>` — tile `1..volumeCount`, `<button aria-pressed>` con etichetta `t('shelf.volumeOwned'|'shelf.volumeMissing', {n})`, classi `.tile`/`.tile.missing`/`.tile.busy`, legenda e `.progress`; più i pulsanti "Segna tutti" / "Nessuno". Riparte da `reference/Shelf.tsx` senza lookahead, volume 0 e `.future`.
- Produces: `<ConfirmDelete what onConfirm>` da `reference/ConfirmDelete.tsx` con `t()`.
- Produces: in `features/collection/api.ts` gli hook TanStack Query `useCollection(filters)`, `useEdition(id)`, `useSetOwned(editionId)` (ottimistico: `onMutate` aggiorna la cache, `onError` ripristina), `useAddToCollection`, `useRemoveFromCollection`.

- [ ] **Step 1: Test che falliscono**:
  - `formatMissing.test.ts`: casi `([], 3)` → `"1–3"`, `([1,2,3], 3)` → `""`, `([1,2,4,5,6,10], 10)` → `"3, 7–9"`, `([2], 3)` → `"1, 3"`.
  - `Shelf.test.tsx`: `togglesVolumeAndReportsNewSet` (clic sul 3 con owned `[1]` → `onChange([1,3])`); `markAllAndNone`; tile possedute `aria-pressed="true"`; nessuna violazione axe.
  - `collection.test.tsx`: `groupsEditionsBySeriesWithProgress` (`110/114`); `incompleteChipFilters` (la query parte con `incomplete=true`); `failedSaveRollsBackTileAndShowsError` (PUT `/volumes` → 500: la tile torna `aria-pressed="false"` e compare `.error`); `wishEditionShowsZeroOwned` (`0/1`).

- [ ] **Step 2: Eseguire** → FAIL. **Step 3: Implementare** `/` (raggruppamento per serie, chip Tipo/Stato/Solo incomplete, ricerca, paginazione) e `/editions/:id` (copertina 120×180 o segnaposto, dati, scaffale, "Mancano: …", aggiungi/togli dalla collezione; azioni admin aggiunte nel Task 11). **Step 4: Eseguire** i controlli del frontend → PASS.

- [ ] **Step 5: Commit** `feat(frontend): collection page and volume shelf`.

---

### Task 11: Frontend — catalogo, serie ed edizioni

**Files:**
- Create: `frontend/src/features/catalog/{api.ts,CatalogPage.tsx,SeriesPage.tsx,SeriesForm.tsx,EditionForm.tsx,CoverInput.tsx,catalog.test.tsx}`
- Modify: `frontend/src/features/collection/EditionPage.tsx` (modifica/elimina per admin, upload copertina con `canEditCover`)

**Interfaces:**
- Consumes: `ConfirmDelete`, `useMe`, tipi `SeriesSummary`, `SeriesDetail`, `EditionResponse`.
- Produces: `<EditionForm initial? onSubmit(values: EditionFormValues)>` riusato per creazione e modifica; `EditionFormValues = { name; publisher; type; status; volumeCount }`; `<CoverInput onSelect(file: File)>` con anteprima `URL.createObjectURL` e accetta `image/jpeg,image/png`.

- [ ] **Step 1: Test che falliscono** in `catalog.test.tsx`:
  - `searchesSeries` (digitazione → query `q`).
  - `createsSeriesAndNavigatesToIt`.
  - `createsEditionThenUploadsCover`: POST edizione, poi PUT multipart sulla `Location` ricevuta.
  - `duplicateTitleShows409Detail` come `.error`.
  - `adminSeesEditAndDelete` / `userDoesNot`.
  - `deleteEditionNamesCollectionCount`: la conferma dice in quante collezioni è presente.
  - `staleEditShowsConflict`: PUT → 409 → messaggio e ricarica dei dati.
  - Nessuna violazione axe su `SeriesPage`.

- [ ] **Step 2: Eseguire** → FAIL. **Step 3: Implementare** `/catalog`, `/series/:id`, i form e le azioni admin. **Step 4: Eseguire** i controlli del frontend → PASS.

- [ ] **Step 5: Commit** `feat(frontend): catalog, series and edition forms`.

---

### Task 12: Frontend — utenti, account e privacy

**Files:**
- Create: `frontend/src/features/users/{api.ts,AdminUsersPage.tsx,users.test.tsx}`
- Create: `frontend/src/features/account/{AccountPage.tsx,account.test.tsx}`, `frontend/src/pages/PrivacyPage.tsx`
- Modify: `docs/privacy.md` (template compilato con i dati della spec § Account e sicurezza → Privacy)

**Interfaces:**
- Consumes: `api`, `ConfirmDelete`, `useMe`, `UserResponse`.

- [ ] **Step 1: Test che falliscono**:
  - `users.test.tsx`: `createsUserWithTemporaryPassword`; `invalidUsernameShowsFieldError`; `lastAdminConflictShown` (409 → `.error`); tabella con `data-label` su ogni cella.
  - `account.test.tsx`: `exportDownloadsJson` (link/bottone che scarica `mangashelf-export.json` dal body di `/account/export`); `deleteAccountRequiresPasswordAndLogsOut`; cambio password come nel Task 9.

- [ ] **Step 2: Eseguire** → FAIL. **Step 3: Implementare** `/admin/users`, `/account`, `/privacy` (testo da `docs/privacy.md`). **Step 4: Eseguire** i controlli del frontend → PASS.

- [ ] **Step 5: Commit** `feat(frontend): admin users, account and privacy pages`.

---

### Task 13: Una sola immagine, dati di sviluppo e consegna

**Files:**
- Create: `backend/src/main/java/me/luucka/mangashelf/config/SpaController.java` (come guida 11), `backend/src/test/java/me/luucka/mangashelf/config/SpaControllerIT.java`
- Create: `backend/src/main/resources/db/dev-data/V2026_11_03_9000__dev_seed.sql` (solo profilo `dev`)
- Modify: `backend/Dockerfile` (template; build dalla root), `README.md` (deploy: porta 8082/8083, variabili `ADMIN_*` nel `.env` del server, `BACKUP_UPLOADS_DIR=/app/uploads`, rate limit NPM su `/api/v1/auth/login`), `CHANGELOG.md` (`## [Unreleased]` con la v1)
- Delete: `docs/superpowers/plans/reference/`
- Modify (repo `project-standards`, branch `claude/great-hopper-o2dsm6`): `apps/README.md` — riga MangaShelf e porte 8082/8083

**Interfaces:**
- Consumes: tutto il resto.

- [ ] **Step 1: Test che fallisce** `SpaControllerIT`: GET `/editions/abc` → 200 con `index.html` (in test un `static/index.html` minimo in `src/test/resources`); GET `/api/v1/unknown` senza login → 401; GET `/assets/x.js` inesistente → 404.

- [ ] **Step 2: Eseguire** → FAIL. **Step 3: Implementare** `SpaController`. **Step 4: Eseguire** `./mvnw verify` → PASS.

- [ ] **Step 5: Dati di sviluppo**: admin `admin` / `admin-dev-password` (hash bcrypt `{bcrypt}…` generato una volta), utente `amico` / `amico-dev-password`, entrambi senza password temporanea; serie *One Piece* con le edizioni Edizione blu (114, REGULAR, ONGOING), Volume 1 - Oro (1, VARIANT, COMPLETED), Volume 100 - Giapponese (1, JAPANESE, COMPLETED), Novel Ace (2, REGULAR, COMPLETED); collezione di `admin` con Edizione blu volumi 1–110 e Oro senza volumi. Verifica: `./mvnw spring-boot:run` + `npm run dev`, login su `http://localhost:5173`, la collezione mostra `110/114` e `0/1`.

- [ ] **Step 6: Immagine**: `docker build -f backend/Dockerfile -t mangashelf:local .` → successo; `docker run` con un Postgres di prova e `curl /actuator/health` → `UP`, `curl /` → HTML della SPA.

- [ ] **Step 7: Verifica finale**: `./mvnw verify` e controlli del frontend → PASS; passaggio da tastiera su login, collezione e scaffale (Tab/Invio/Spazio, focus visibile).

- [ ] **Step 8: Commit** `feat: serve frontend from backend image, dev seed data and docs`; commit separato in `project-standards` `docs(apps): add MangaShelf ports and domain row`; push di entrambi i branch.
