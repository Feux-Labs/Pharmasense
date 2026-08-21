# Pharmasense Backend — Build Guide

This is a from-scratch walkthrough of the backend: what was built, why it's structured the way it is, the Spring Boot/Java concepts it leans on, how to run it, how to test it, and how to extend it. Read it top to bottom the first time; use it as a reference after that.

---

## 1. What this is

Pharmasense is a multi-tenant pharmacy operating system: inventory with batch/expiry tracking, prescriptions, QR-code shelf scanning, offline-first sync for a counter with bad internet, a natural-language AI assistant that can actually perform actions, and a super-admin side to run the platform.

**Architecture philosophy: modular monolith now, microservices-ready later.** Everything runs in one Spring Boot process today, but it's cut into *feature modules* (`inventory`, `prescription`, `sync`, `agent`, …) that only talk to each other through their service classes — never by reaching into another module's repository or entity internals from outside it. That discipline is what makes it possible to later lift a whole module (say, `inventory`) out into its own deployable service: its controllers become the new service's API, its service classes stay almost untouched, and every *other* module that called it in-process now calls it over HTTP instead. Section 14 below walks through that extraction step by step. Building it this way from day one is much cheaper than retrofitting boundaries onto a tangled monolith later.

---

## 2. Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language / runtime | Java 21 (LTS), compiled and run fine on the installed JDK 25 too | Records, sealed interfaces, virtual threads, pattern matching in `switch` — all used throughout this codebase |
| Framework | Spring Boot 4.1 (Spring Framework 7) | Current stable line as of this build; modular starters (`spring-boot-starter-webmvc`, `-security-oauth2-client`, etc.) instead of one do-everything `spring-boot-starter-web` |
| Build tool | Maven, via the bundled `mvnw`/`mvnw.cmd` wrapper | No local Maven install required |
| Database | PostgreSQL (Neon in production, local Docker for dev) | Relational, strong constraints, JSONB available if ever needed, Neon gives a generous free tier with branching |
| Schema migrations | Flyway | Versioned, plain SQL, no "magic" schema generation from entities in prod |
| ORM | Spring Data JPA / Hibernate | Standard, well understood, plays well with Flyway when `ddl-auto=validate` |
| Cache / ephemeral state | Redis (Upstash in production, local Docker for dev) | OTP codes, `@Cacheable` read-through caches, the offline "snapshot" bundle |
| Auth | Custom JWT (access token) + opaque rotating refresh tokens, email+OTP via ZeptoMail, Google OAuth2 login | See §7 |
| AI | Anthropic Claude Messages API, tool-use (function calling) | See §9 |
| API docs | springdoc-openapi (Swagger UI at `/v1/swagger-ui.html`) | Try every endpoint from a browser without writing a client |
| Object mapping | MapStruct (simple 1:1 entity↔DTO mappers) + hand-written mapping where computed fields are involved | Keeps repetitive mapping code out of services without hiding business logic in generated code |
| QR codes | ZXing | The reference open-source Java QR library |
| Testing | JUnit 5, AssertJ, Mockito, Testcontainers (Postgres + Redis), Spring's `MockMvc` | See §12 |

---

## 3. Prerequisites & one-time setup

You need: **Java 21+** (already installed) and **Docker Desktop** (for local Postgres/Redis and for running the integration test suite — Testcontainers spins up real throwaway containers per test run). You do **not** need Maven installed — `./mvnw.cmd` downloads the exact Maven version this project expects the first time you run it.

```bash
cd Backend
cp .env.example .env
```

Open `.env` and fill in at minimum:
- `JWT_SECRET` — generate with `openssl rand -base64 48` (or any 32+ byte random string)
- `ZEPTOMAIL_API_TOKEN`, `ZEPTOMAIL_FROM_EMAIL` — from your ZeptoMail account, or leave `ZEPTOMAIL_SEND_ENABLED=false` to just log OTP emails to the console while developing
- `GROQ_API_KEY` — from console.groq.com, only needed to use the `/agent/chat` endpoint (the assistant runs on a Llama model hosted by Groq)
- `GOOGLE_OAUTH_CLIENT_ID` / `GOOGLE_OAUTH_CLIENT_SECRET` — from Google Cloud Console, only needed to test Google sign-in

Everything else has a sane local default already.

### Start Postgres + Redis

```bash
docker compose up -d
```

This starts exactly what `docker-compose.yml` describes: a `postgres:17-alpine` container on `5432` and a `redis:7-alpine` container on `6379`, both pre-configured to match the defaults in `application.yml`.

### Run the app

```bash
./mvnw.cmd spring-boot:run
```

(on macOS/Linux: `./mvnw spring-boot:run`)

Flyway runs automatically on startup and creates every table. Once it's up:
- API base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/v1/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

### Bootstrap your own super-admin account

Set `SUPER_ADMIN_BOOTSTRAP_EMAIL=you@example.com` in `.env`, restart the app once, then sign in with the normal email+OTP flow (`POST /api/v1/auth/otp/request` then `/otp/verify`) — no separate admin password to manage. See `SuperAdminBootstrapRunner`.

---

## 4. Project structure

```
Backend/
  pom.xml                        Maven build file - every dependency and the compiler plugin config
  docker-compose.yml             Local Postgres + Redis
  .env.example                   Every environment variable the app reads, documented
  src/main/resources/
    application.yml              Base config, works out of the box against docker-compose
    application-docker.yml       Profile for when the app itself also runs in a container
    application-prod.yml         Profile for a real deployment (Neon/Upstash TLS, log levels)
    db/migration/                Flyway SQL migrations, V1 through V5, run in order on startup
  src/main/java/com/pharmasense/
    common/                      Shared building blocks every feature module depends on (never the other way around)
    tenant/                      The Pharmacy entity - the tenant every other module scopes its data to
    identity/                    Users, roles/permissions, JWT, OTP, Google OAuth2, Spring Security config
    notification/                ZeptoMail transactional email client
    inventory/                   Products, batches, stock movements, FEFO, expiry tracker
    prescription/                Patients and prescriptions, including FEFO-based fill logic
    catalog/                     QR code generation + scan resolution for inventory items/batches
    sync/                        Offline-first: full snapshot download, incremental pull, push with idempotency
    agent/                       The AI assistant: Claude client, tool definitions, conversation loop
    admin/                       Super-admin: cross-tenant analytics, user impersonation
  src/test/java/com/pharmasense/
    TestcontainersConfiguration.java   Real Postgres+Redis containers for integration tests
    .../*IntegrationTest.java          Full HTTP-level flows against those containers
    .../*Test.java                     Fast unit tests, no containers
```

### Anatomy of a feature module

Every module (`inventory`, `prescription`, `sync`, …) follows the same shape, deliberately, so once you understand one you understand all of them:

```
inventory/
  entity/       JPA @Entity classes - the database shape
  enums/        Fixed vocabularies (StockLevelStatusEnum, StockMovementTypeEnum, ...)
  repository/   Spring Data JPA interfaces - just method signatures, Spring writes the queries
  service/      Business logic. This is the ONLY layer other modules are allowed to call into.
  controller/   REST endpoints - thin, just: auth check, call a service method, wrap the result
  dto/          Request/response shapes (Java records) - never expose an @Entity directly over the API
  mapper/       Entity <-> DTO conversion (MapStruct, or hand-written when a field is computed)
```

The rule that keeps this maintainable: **a module only ever imports another module's `service` package (or a couple of small shared `dto`/`entity` types where needed), never its `repository` or raw `entity` internals for writes.** e.g. `PrescriptionService` calls `InventoryBatchService.adjustQuantity(...)` to draw down stock when filling a prescription — it never touches `InventoryBatchRepository` directly. That's the seam a future microservice split happens along.

---

## 5. Core concepts, with real code from this repo

### 5.1 The base entity and tenant scoping

Every table needs an id, audit timestamps, and optimistic-locking version. Every *tenant-owned* table additionally needs a `pharmacy_id`. Rather than repeat that on 15 entities, two `@MappedSuperclass` base classes do it once:

```java
// common/domain/AuditableEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {
    @Id @GeneratedValue @UuidGenerator
    private UUID id;

    @CreatedDate private Instant createdAt;
    @LastModifiedDate private Instant updatedAt;

    @Version private long version;   // optimistic locking
}

// common/domain/TenantScopedEntity.java
@MappedSuperclass
public abstract class TenantScopedEntity extends AuditableEntity {
    @Column(nullable = false, updatable = false)
    private UUID pharmacyId;
}
```

`InventoryItemEntity extends TenantScopedEntity` and gets `id`, `createdAt`, `updatedAt`, `version`, and `pharmacyId` for free. IDs are UUIDs generated in the JVM (Hibernate's `@UuidGenerator`), not database sequences — that matters for offline sync, because a client can generate a valid ID for a record *before* it ever reaches the server.

### 5.2 Flyway migrations, not Hibernate auto-DDL

`application.yml` sets `spring.jpa.hibernate.ddl-auto: validate` — Hibernate is only ever allowed to check the schema matches what the entities expect; it never creates or alters tables itself. Real schema changes are plain SQL files:

```sql
-- src/main/resources/db/migration/V2__inventory.sql
create table inventory_items (
    id                          uuid primary key,
    created_at                  timestamptz not null,
    ...
    pharmacy_id                 uuid not null references pharmacies (id),
    name                        varchar(255) not null,
    unit_selling_price          numeric(12, 2) not null,
    qr_code                     varchar(64),
    active                      boolean not null default true,
    constraint uk_inventory_items_qr_code unique (qr_code)
);
```

Flyway runs every `V{n}__description.sql` file in `db/migration` in order, once, tracked in a `flyway_schema_history` table it manages itself. **To change the schema: add a new `V6__whatever.sql` file — never edit an already-applied one.**

### 5.3 DTOs are records, validated with Bean Validation

Every request/response shape is a Java `record` (immutable, concise, free `equals`/`hashCode`/`toString`), annotated with `jakarta.validation` constraints that Spring enforces automatically before the controller method even runs:

```java
public record InventoryItemCreateRequest(
        @NotBlank String name,
        String genericName,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal unitSellingPrice,
        boolean requiresPrescription,
        @Min(0) Integer lowStockThreshold) {
}
```

```java
@PostMapping
public ApiResponse<InventoryItemResponse> create(
        @AuthenticationPrincipal PharmasenseUserPrincipal principal,
        @Valid @RequestBody InventoryItemCreateRequest request) {   // <- @Valid triggers the checks
    ...
}
```

If validation fails, `GlobalExceptionHandler` catches `MethodArgumentNotValidException` and returns a `400` with a readable message — the controller method body never runs, so it never has to check "is this null?" itself.

### 5.4 Repositories: Spring writes the SQL

```java
public interface InventoryItemRepository extends JpaRepository<InventoryItemEntity, UUID> {
    Optional<InventoryItemEntity> findByIdAndPharmacyId(UUID id, UUID pharmacyId);
    Page<InventoryItemEntity> findByPharmacyIdAndActiveTrueAndNameContainingIgnoreCase(
            UUID pharmacyId, String nameFragment, Pageable pageable);
}
```

Spring Data JPA parses the method name and generates the query at startup - no SQL or JPQL written for the common cases. When a query doesn't fit that pattern (an aggregate, a `max()`), drop to `@Query`:

```java
@Query("select max(s.sequenceNumber) from SyncChangeLogEntity s where s.pharmacyId = :pharmacyId")
Long findMaxSequenceNumberByPharmacyId(@Param("pharmacyId") UUID pharmacyId);
```

### 5.5 Services own transactions and business rules

```java
@Transactional
public InventoryBatchEntity adjustQuantity(
        UUID pharmacyId, UUID batchId, StockMovementTypeEnum movementType,
        int quantityDelta, String reason, UUID performedByUserId) {

    InventoryBatchEntity batch = getByIdForPharmacy(pharmacyId, batchId);
    int newQuantity = batch.getQuantityOnHand() + quantityDelta;
    if (newQuantity < 0) {
        throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                "Adjustment would take batch " + batch.getBatchNumber() + " below zero stock");
    }
    batch.setQuantityOnHand(newQuantity);
    InventoryBatchEntity saved = inventoryBatchRepository.save(batch);

    recordMovement(saved, movementType, quantityDelta, reason, performedByUserId);  // audit trail
    recordSyncChange(saved, SyncOperationEnum.UPDATE);                              // offline sync log
    return saved;
}
```

`@Transactional` means: if anything after `save()` throws, the quantity change, the movement-log row, and the sync-log row **all roll back together**. This is the pattern behind every write in the codebase — a mutation and everything that must stay consistent with it happen in one transaction.

### 5.6 Controllers are thin

```java
@PostMapping("/batches/{batchId}/adjust")
@PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_WRITE')")
public ApiResponse<InventoryBatchResponse> adjustBatch(
        @AuthenticationPrincipal PharmasenseUserPrincipal principal,
        @PathVariable UUID batchId,
        @Valid @RequestBody StockAdjustmentRequest request) {
    InventoryBatchEntity batch = inventoryBatchService.adjustQuantity(
            principal.pharmacyId(), batchId, request.movementType(), request.quantityDelta(), request.reason(), principal.userId());
    return ApiResponse.ok(inventoryBatchService.toResponse(batch, expiryWarningDays), "Stock adjusted");
}
```

Auth check, delegate, wrap response. All business logic lives in the service. Every endpoint returns the same envelope:

```java
public record ApiResponse<T>(boolean success, T data, String errorCode, String message, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) { ... }
    public static <T> ApiResponse<T> error(String errorCode, String message) { ... }
}
```

so a frontend (or the AI agent's tool layer) only ever has to parse one shape.

### 5.7 Configuration as typed records

Every config value under `pharmasense.*` in `application.yml` is bound to a typed, immutable record instead of scattered `@Value("${...}")` strings:

```java
@ConfigurationProperties(prefix = "pharmasense.jwt")
public record JwtProperties(String secret, String issuer, int accessTokenTtlMinutes, int refreshTokenTtlDays) {}
```

registered once via `@EnableConfigurationProperties(JwtProperties.class)`, then just constructor-injected anywhere it's needed - typo-proof (Spring fails fast at startup if a property is missing/misnamed) and IDE-autocompletable.

---

## 6. Authentication, RBAC & multi-tenancy (`identity/`, `tenant/`)

### 6.1 Two ways to sign in, one token format

**Email + OTP** (`AuthenticationService`, `OtpService`):
1. `POST /api/v1/auth/signup` creates a `PharmacyEntity` + an `OWNER` `UserAccountEntity`, then sends a 6-digit code.
2. The code is hashed (`TokenHasher.sha256`) and stored **in Redis only**, key `otp:code:{email}`, TTL 10 minutes, with an attempt counter and a separate cooldown key to stop resend-spamming.
3. `POST /api/v1/auth/otp/verify` checks the code, then issues tokens.

**Google OAuth2** (`SecurityConfig`, `CustomOidcUserService`, `OAuth2AuthenticationSuccessHandler`): standard Spring Security `oauth2Login()` redirect flow. On success, `CustomOidcUserService` finds-or-creates the local user, and the success handler mints the same kind of tokens and redirects the browser to `{frontend}/oauth2/callback?accessToken=...&refreshToken=...`. A Google sign-in has no pharmacy attached yet (`pharmacyId == null`) - the frontend should route that case to a short "name your pharmacy" step that calls `POST /api/v1/auth/complete-pharmacy-setup`.

**Both paths converge on the same token pair:**
- **Access token**: a signed JWT (`JwtService`, HMAC-SHA256), 15 minutes, carries `userId`, `pharmacyId`, `role`, `email` as claims. Stateless - no database lookup on every request.
- **Refresh token**: an opaque random string, *not* a JWT. Only its SHA-256 hash is stored (`RefreshTokenEntity.tokenHash`). `POST /api/v1/auth/refresh` **rotates** it - the old one is marked revoked and a new one issued in the same call. If a revoked token is ever presented again (a strong signal of theft), every active session for that user is revoked (`RefreshTokenService.rotate`).

### 6.2 Fine-grained RBAC, not just role checks

Four roles (`OWNER`, `PHARMACIST`, `STAFF`, `SUPER_ADMIN`) map to sets of specific `PermissionEnum` values:

```java
public enum UserRoleEnum {
    OWNER(Set.of(INVENTORY_READ, INVENTORY_WRITE, INVENTORY_DELETE, PRESCRIPTION_READ, PRESCRIPTION_WRITE,
                 CATALOG_SCAN, STAFF_MANAGE, PHARMACY_SETTINGS_MANAGE, ANALYTICS_VIEW, AGENT_USE)),
    PHARMACIST(Set.of(INVENTORY_READ, INVENTORY_WRITE, PRESCRIPTION_READ, PRESCRIPTION_WRITE, CATALOG_SCAN, ANALYTICS_VIEW, AGENT_USE)),
    STAFF(Set.of(INVENTORY_READ, INVENTORY_WRITE, PRESCRIPTION_READ, CATALOG_SCAN, AGENT_USE)),
    SUPER_ADMIN(Set.of());   // handled separately - see below
    ...
}
```

Controllers check a *permission*, never a role name directly:

```java
@DeleteMapping("/{itemId}")
@PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'INVENTORY_DELETE')")
public ApiResponse<Void> delete(...) { ... }
```

`RbacEvaluator` is a plain Spring bean referenced by name (`@rbacEvaluator`) from the `@PreAuthorize` SpEL expression - `SUPER_ADMIN` always passes (it operates through a different, path-restricted set of endpoints instead), everyone else is checked against their role's permission set. Want to give `STAFF` the ability to delete inventory tomorrow? Change one line in `UserRoleEnum`. No controller touched.

Coarse-grained gating happens once, in `SecurityConfig`:

```java
.requestMatchers("/api/v1/admin/**").hasRole("SUPER_ADMIN")
.anyRequest().authenticated()
```

### 6.3 Multi-tenancy

Every tenant-owned entity extends `TenantScopedEntity` (§5.1). Every repository method that reads or writes one takes `pharmacyId` as a required parameter, sourced from the authenticated principal (`principal.pharmacyId()`) - **never** from anything client-supplied in the request body or query string. This is what stops pharmacy A from ever seeing pharmacy B's data through a normal API call. The one deliberate exception is the `admin/` module, which is explicitly allowed to query across every tenant, gated entirely on `SUPER_ADMIN`.

---

## 7. Offline-first sync (`sync/`)

Three endpoints, three different jobs:

| Endpoint | Job | When the client calls it |
|---|---|---|
| `GET /api/v1/sync/snapshot` | Full bundle: every active inventory item + batch, patient, and prescription for the pharmacy | Once, right after coming online (or periodically) — this is the "download everything so it works offline" step |
| `GET /api/v1/sync/pull?since={cursor}` | Everything that changed after a cursor | Frequently, while online — cheap incremental catch-up |
| `POST /api/v1/sync/push` | Replay stock adjustments queued while offline | Once back online, before pulling |

### 7.1 The change log

Every mutating write in `inventory` and `prescription` calls `SyncChangeRecorder.record(...)` in the *same transaction* as the actual save:

```java
InventoryItemEntity saved = inventoryItemRepository.save(item);
InventoryItemResponse response = toResponse(saved, pharmacyService.getById(pharmacyId));
syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.INVENTORY_ITEM, saved.getId(), SyncOperationEnum.CREATE, response);
```

That appends a row to `sync_change_log`, whose `sequence_number` column is a Postgres `bigserial` - separate from the UUID primary key, purely so it's *sortable*. A client stores the highest `sequenceNumber` it has seen (`nextCursor` in the pull response) and passes it back as `since` next time: "give me everything after N."

### 7.2 Push, and why idempotency matters

A flaky connection means the same offline mutation can be submitted twice. Each pushed item carries a client-generated `clientOperationId`; before applying anything, `SyncStockAdjustmentApplier` checks `sync_push_receipts` for that id + pharmacy. Already there → return `ALREADY_APPLIED` without touching stock again. Not there → apply it, then record the receipt, in one transaction, so a crash between "apply" and "record receipt" can never happen silently.

```java
@Transactional
public SyncPushResultDto apply(UUID pharmacyId, UUID performedByUserId, PendingStockAdjustmentDto adjustment) {
    if (syncPushReceiptRepository.findByPharmacyIdAndClientOperationId(pharmacyId, adjustment.clientOperationId()).isPresent()) {
        return new SyncPushResultDto(adjustment.clientOperationId(), ALREADY_APPLIED, "...");
    }
    // apply, then saveReceipt(...) - same transaction
}
```

Each item in a push batch is applied in **its own** transaction (via a separate bean, `SyncStockAdjustmentApplier` - see the class Javadoc for why it has to be a separate bean rather than a private method, it's a real Spring gotcha worth understanding: a `@Transactional` method calling itself through `this` inside the same class silently does *not* get intercepted by Spring's proxy). One conflicting item never blocks the rest of the batch.

**Scope note**: push currently only replays stock adjustments - the single most common offline action at a pharmacy counter. Extending it to another entity type (e.g. offline-created patients) means adding a sibling `Pending*Dto` + applier bean following the same shape. See the Javadoc on `SyncPushService` for the exact extension point.

---

## 8. QR / catalog scanning (`catalog/`)

Every inventory item and every batch can get a short scan code (`ITM-XXXXXXXX` / `BAT-XXXXXXXX`, `CatalogCodeGenerator` - deliberately not a UUID, which would need a denser, harder-to-scan QR grid at sticker size):

```
POST /api/v1/catalog/items/{itemId}/qrcode      -> assigns + returns the code
GET  /api/v1/catalog/qrcode/{code}.png          -> the printable QR image itself
GET  /api/v1/catalog/scan/{code}                -> resolves a scanned code to live data
```

A QR encodes `{frontendBaseUrl}/scan/{code}` - a normal URL. The staff PWA's `/scan/:code` route opens, and calls the `GET /scan/{code}` API with the logged-in user's bearer token. Scanning an **item** code (a shelf label) returns that product's aggregate status plus every batch behind it; scanning a **batch** code (an individual sticker) returns just that batch plus enough product context to show it standalone. This is deliberately **staff-authenticated, not public** - the response includes quantity and cost data a pharmacy wouldn't want exposed by anyone photographing a shelf label. `CatalogScanService` also double-checks the resolved item/batch actually belongs to the scanning user's pharmacy before returning anything, even though codes are looked up globally by value (defense against a code being guessed or leaked across tenants).

---

## 9. The agentic AI assistant (`agent/`)

One endpoint: `POST /api/v1/agent/chat` — `{"message": "add 50 paracetamol at 1200 each"}` in, a plain-language reply out, with the action actually performed in between.

### 9.1 How the loop works (`AgentConversationService`)

1. Send the conversation + a filtered list of tool definitions to Claude (`ClaudeApiClient`, Anthropic's Messages API directly over `WebClient` - no SDK dependency).
2. If Claude's response is plain text → done, return it.
3. If Claude asks to use a tool (`stop_reason: "tool_use"`) → run it through `AgentToolRegistry.dispatch(...)`, append the tool's result to the conversation, go back to step 1.
4. Repeat up to `pharmasense.agent.max-tool-iterations` times (default 6) as a safety cap against a runaway loop.

### 9.2 Tools are thin adapters over existing services - never new business logic

```java
@Component
public class AdjustStockTool implements AgentTool {
    public PermissionEnum requiredPermission() { return PermissionEnum.INVENTORY_WRITE; }

    public Object execute(AgentToolContext context, JsonNode input) {
        // ... find the item by name, pick the FEFO batch ...
        InventoryBatchEntity updated = inventoryBatchService.adjustQuantity(
                context.pharmacyId(), batch.getId(), movementType, quantityDelta, reason, context.userId());
        return Map.of("itemName", item.name(), "newQuantityOnHand", updated.getQuantityOnHand());
    }
}
```

Six tools ship today: `check_stock_balance`, `search_inventory`, `add_inventory_item`, `adjust_stock`, `check_expiring_items`, `lookup_prescriptions`. Adding a seventh means implementing `AgentTool` (name, description, JSON-schema parameters via the small `JsonSchema` builder, required permission, `execute(...)`) and marking it `@Component` - Spring autowires every `AgentTool` bean into `AgentToolRegistry` automatically, nothing else to wire up.

### 9.3 The security property that matters most here

```java
public record AgentToolContext(UUID pharmacyId, UUID userId, UserRoleEnum role) {}
```

Every tool receives this, built server-side from the authenticated principal - **never** from anything in the model's tool-call input. A prompt-injected or hallucinated "pharmacyId" in the conversation text can never cause a cross-tenant read or write, because tools are never given the option to use one. `AgentToolRegistry` also filters *which tools are even offered* to Claude based on the caller's RBAC permissions (so a `STAFF` user is never offered `add_inventory_item` in the first place), and re-checks permission again at dispatch time - defense in depth.

---

## 10. Super-admin (`admin/`)

- `GET /api/v1/admin/tenants`, `/api/v1/admin/users`, `/api/v1/admin/analytics/overview` - cross-tenant reads, restricted entirely by the `SecurityConfig` path rule `hasRole("SUPER_ADMIN")`.
- `POST /api/v1/admin/users/{userId}/impersonate` - "click on their account and do anything they can do." Mints a short-lived access token carrying **the target user's own role and pharmacy**, not admin privileges - the frontend swaps its active token for this one and the app renders exactly as that user would see it. Every grant is logged to `impersonation_audit_log` (`ImpersonationService`) before the token is issued.

```java
String accessToken = jwtService.generateAccessToken(targetUser, superAdminUserId, adminProperties.impersonationTtlMinutes());
```

The `impersonatedBy` claim rides along in the token (`PharmasenseUserPrincipal.impersonatedBy()`), so any service that wants to distinguish "the real user did this" from "an admin acting on their behalf did this" can check `principal.isImpersonated()`. Only the impersonation *grant* itself is audited in this release, not every subsequent action taken during the session - see §13 for that as a documented extension point.

---

## 11. Running it

```bash
docker compose up -d          # Postgres + Redis
./mvnw.cmd spring-boot:run    # the app, on :8080
```

Try it against Swagger UI (`http://localhost:8080/v1/swagger-ui.html`), or with curl:

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"pharmacyName":"Apotek Melati","ownerFullName":"Jane Owner","ownerEmail":"jane@example.com","currencyCode":"USD"}'

# with ZEPTOMAIL_SEND_ENABLED=false, the OTP code is printed to the app's console log instead of emailed
curl -X POST http://localhost:8080/api/v1/auth/otp/verify \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","code":"123456"}'
```

---

## 12. Testing

### 12.1 Two kinds of test in this repo

**Unit tests** — no Spring context, no containers, milliseconds each. Test pure logic in isolation, e.g.:

```java
class InventoryStatusCalculatorTest {
    private final InventoryStatusCalculator calculator = new InventoryStatusCalculator();

    @Test
    void quantityAtOrBelowThresholdIsLowStock() {
        assertThat(calculator.computeStockLevelStatus(10, 10)).isEqualTo(StockLevelStatusEnum.LOW_STOCK);
    }
}
```

Run just these (fast, no Docker needed):

```bash
./mvnw.cmd test -Dtest="InventoryStatusCalculatorTest,RbacEvaluatorTest,JwtServiceTest,CatalogCodeGeneratorTest,OtpServiceTest"
```

These five classes (27 test methods) all pass as of this build.

**Integration tests** — full Spring context, real Postgres + Redis via Testcontainers, real HTTP requests through `MockMvc`. These need **Docker Desktop running**:

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowIntegrationTest {
    @MockitoSpyBean private EmailService emailService;   // intercept the OTP instead of actually emailing it

    @Test
    void receivingStockThenSellingItUpdatesAggregateStatus() throws Exception {
        String ownerToken = registerAndLogin("Apotek Melati " + UUID.randomUUID());
        // ... create an item, receive a batch, oversell (expect 409), sell validly (expect the new quantity) ...
    }
}
```

Run everything, including these:

```bash
./mvnw.cmd test
```

`TestcontainersConfiguration` (in `src/test/java/com/pharmasense/`) declares the containers once, shared by every integration test via Spring Boot's `@ServiceConnection` — no manual JDBC URL/Redis host wiring needed, Spring auto-configures the datasource and Redis connection factory to point at whatever port Testcontainers picked.

Three integration test classes ship today, covering the parts worth the most confidence:
- `AuthenticationFlowIntegrationTest` — signup → OTP verify → access a protected endpoint; wrong code is rejected; missing token is rejected.
- `InventoryFlowIntegrationTest` — create item → receive batch → aggregate status updates → over-sell is rejected (409) and doesn't touch stock → valid sale reduces quantity; a `STAFF`-role user can read but not delete (403 RBAC check).
- `PrescriptionFlowIntegrationTest` — the FEFO fill logic specifically: two batches at different expiry dates, filling a prescription draws from the sooner-expiring one first; filling with insufficient stock is rejected **atomically** (prescription stays `PENDING`, no batch is touched).

> **A note on this environment**: these integration tests were written and verified to *compile* cleanly, but this build environment's Docker Desktop could not start its engine (`Could not find a valid Docker environment` from Testcontainers) - likely no nested virtualization available in this sandbox. Run `./mvnw.cmd test` yourself with Docker Desktop actually running to execute them; there is nothing else blocking it.

### 12.2 The Mockito self-invocation gotcha, seen twice in this codebase

Worth understanding once: `@Transactional` (and `@Cacheable`, `@PreAuthorize`, anything AOP-based) works by Spring wrapping your bean in a proxy. If a method on that bean calls *another method on the same class* via `this.otherMethod()`, that call bypasses the proxy entirely — the annotation on `otherMethod` is silently ignored. This is why `SyncStockAdjustmentApplier` is a separate `@Service` bean rather than a private method inside `SyncPushService`: `SyncPushService.applyStockAdjustments(...)` needs each item applied in *its own* transaction, and that only works because it's calling out to a different bean, going through that bean's proxy.

---

## 13. Known scope limitations (read before extending)

Built deliberately, not accidentally missing — but worth knowing before you build on top of them:

- **Offline push** only replays stock adjustments (§7.2). Extending it to other offline-writable entities follows a documented pattern but isn't done for you.
- **Snapshot bundle** (`GET /sync/snapshot`) caps at 1000 rows per entity type. A pharmacy past that size needs pagination/streaming added - `TenantSnapshotService`'s Javadoc flags exactly where.
- **Impersonation audit** logs the *grant* (who impersonated whom, when), not every individual action taken during the session. The `impersonatedBy` JWT claim is there for any service that wants to thread deeper per-action audit through later.
- **Billing/invoicing/GST-style tax compliance** was scoped out of this pass entirely (it wasn't in the original ask, and it's a large feature on its own) - `PharmacyEntity` has a generic `currencyCode` and `taxRegistrationNumber` so it isn't blocked, but there's no invoice generation, tax calculation, or payment tracking yet.
- **Conversation history** for the AI agent is client-supplied per request (§9), not stored server-side. Fine for now; add a `Conversation`/`ConversationTurn` entity pair if you want server-side thread persistence later.
- **Google OAuth2 signup** creates a user with no pharmacy attached until `/auth/complete-pharmacy-setup` is called (§6.1) - make sure the frontend actually implements that follow-up step.

---

## 14. Adding a new feature module

Follow the shape in §4. Concretely, to add e.g. a `supplier` module (suppliers + purchase orders):

1. `supplier/entity/SupplierEntity.java` extends `TenantScopedEntity`.
2. `supplier/repository/SupplierRepository.java` extends `JpaRepository<SupplierEntity, UUID>`, tenant-scoped finder methods.
3. `supplier/service/SupplierService.java` - the only layer other modules may call into. `@Transactional` on every write method. Call `syncChangeRecorder.record(...)` after each write if this entity should be offline-syncable (see §7.1).
4. `supplier/dto/` - request/response records with `jakarta.validation` annotations.
5. `supplier/mapper/SupplierMapper.java` - a one-line MapStruct interface, unless the response needs computed fields (then build it by hand in the service, like `InventoryItemService.toResponse`).
6. `supplier/controller/SupplierController.java` - thin, `@PreAuthorize("@rbacEvaluator.hasPermission(authentication, '...')")` per endpoint (add a new `PermissionEnum` value first if needed, and assign it to the right roles in `UserRoleEnum`).
7. `db/migration/V6__supplier.sql` - the table, matching the entity's columns exactly.
8. Tests: a fast unit test for any non-trivial pure logic, an integration test for the end-to-end HTTP flow (copy the shape of `InventoryFlowIntegrationTest`).

Run `./mvnw.cmd compile` after each step - the modular structure means most mistakes show up as a compile error pointing at exactly the missing piece.

---

## 15. Roadmap: splitting into real microservices later

When one module's load/team/deploy-cadence genuinely justifies its own service (don't do this prematurely - a modular monolith scales further than people expect):

1. Pick the module (say, `inventory`). Its `controller` package **is** the API contract already - copy it into a new Spring Boot project largely unchanged.
2. Its `service` package becomes that new service's core logic.
3. Every *other* module that called `InventoryItemService`/`InventoryBatchService` in-process (`prescription`, `sync`, `agent`, `catalog`) needs those calls replaced with an HTTP (or gRPC) client call to the new service instead. Because those call sites already only ever went through the service layer (§4's rule), this is a mechanical swap, not a redesign.
4. `inventory`'s tables move to their own database. `pharmacy_id`-based tenant scoping doesn't change - just which physical database enforces it.
5. Repeat per module as/when it earns it. `identity` (auth) is usually the last one worth extracting, since almost everything depends on it.

---

## 16. API reference (grouped by module)

All endpoints are under `/api/v1` unless noted, all responses wrapped in `ApiResponse<T>` (§5.6), all requiring a `Bearer` access token unless marked public.

| Module | Endpoint | Notes |
|---|---|---|
| Auth | `POST /auth/signup` | public |
| | `POST /auth/otp/request` | public |
| | `POST /auth/otp/verify` | public — returns tokens |
| | `POST /auth/refresh` | public — rotates refresh token |
| | `POST /auth/logout` | |
| | `POST /auth/complete-pharmacy-setup` | for a Google sign-in with no pharmacy yet |
| | `GET /oauth2/authorization/google` | public — starts Google login redirect |
| Users | `GET /users/me`, `GET /users`, `POST /users/invite` | `STAFF_MANAGE` for list/invite |
| Pharmacy | `GET /pharmacy/me`, `PATCH /pharmacy/me` | `PHARMACY_SETTINGS_MANAGE` for update |
| Inventory | `GET|POST /inventory/items`, `GET|PUT|DELETE /inventory/items/{id}` | |
| | `GET /inventory/items/expiry-tracker?withinDays=90` | |
| | `GET|POST /inventory/items/{id}/batches` | |
| | `POST /inventory/batches/{id}/adjust` | |
| Prescriptions | `GET|POST /patients`, `GET /patients/{id}` | |
| | `GET|POST /prescriptions`, `GET /prescriptions/{id}`, `PATCH /prescriptions/{id}/status` | |
| Catalog | `POST /catalog/items/{id}/qrcode`, `POST /catalog/batches/{id}/qrcode` | |
| | `GET /catalog/scan/{code}`, `GET /catalog/qrcode/{code}.png` | |
| Sync | `GET /sync/snapshot`, `GET /sync/pull?since=`, `POST /sync/push` | |
| Agent | `POST /agent/chat` | `AGENT_USE` |
| Admin | `GET /admin/tenants`, `GET /admin/tenants/{id}` | `SUPER_ADMIN` only |
| | `GET /admin/users?pharmacyId=`, `GET /admin/users/{id}` | `SUPER_ADMIN` only |
| | `GET /admin/analytics/overview` | `SUPER_ADMIN` only |
| | `POST /admin/users/{id}/impersonate` | `SUPER_ADMIN` only |

Full request/response shapes for every endpoint: run the app and open Swagger UI.
