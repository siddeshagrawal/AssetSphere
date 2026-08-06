# Reference Project Analysis

## Scope and read-only guarantee

This analysis was performed read-only against `C:\Users\user\Downloads\razorpay` and `C:\Users\user\Downloads\lovable-clone`. No command that writes build output, caches, configuration, or source was run in either repository. Secret values found in reference configuration were deliberately not copied or reproduced here.

## Reference summaries

| Project | Actual stack | Primary strengths | Limits for AssetSphere |
|---|---|---|---|
| Razorpay | Spring Boot 4.0.6, Java 25, JPA, Redis, Kafka, Security, JWT, MapStruct | Idempotency filter/store, Redis rate limiting, payment outbox, retry/DLQ workflow, provider routers | Its development configuration contains inline secrets and its idempotency store fails open; neither is suitable unchanged for compliance-sensitive asset operations. |
| Lovable Clone | Spring Boot 4.0.0, Java 21, JPA, Security, JWT, MapStruct, Stripe | Conventional controllers/services/DTOs/mappers, method security expressions, payment-processor interface | It does not actually contain MinIO, Spring AI, RAG, vector-store, or tool-calling implementations. `FileServiceImpl` is a stub. |
| AssetSphere | Spring Boot 3.4.2, Java 21, Spring Modulith 1.3.2, JPA, Redis, Kafka, Flyway, MinIO, Spring AI model API | Documented modular-monolith design, dev/prod profiles, infrastructure interfaces, observability bootstrap | Business modules and their use-case-driven cross-cutting foundations are intentionally not implemented yet. |

AssetSphere must remain on Boot 3.4 / Java 21 for this hackathon phase. Upgrading only to imitate either reference would create unnecessary risk and is not justified.

## Coding, package, and naming conventions

Both references use lower-case package segments; concrete Spring components live in `controller`, `service`, `service.impl`, `repository`, `entity`, `dto`, `mapper`, `security`, and `config`. Interfaces are named by capability and implementations use an `Impl` suffix where there are multiple collaborators or a clear boundary. Records are used for response/request DTOs in Lovable Clone; Razorpay uses classes in several older DTOs.

AssetSphere adopts the vertical-slice equivalent: each implemented module owns `api`, `domain`, `dto`, `mapper`, and persistence-facing types as needed. Cross-cutting abstractions remain in `modules.common`; external-client implementations remain in `infrastructure`. AssetSphere will not copy either reference namespace or payment/project domain model.

## API and exception conventions

Razorpay returns a typed `ErrorResponse` from `GlobalExceptionHandler` and maps validation errors to field errors. Source evidence: `common/exception/GlobalExceptionHandler.java`, `common/exception/ErrorResponse.java`.

Lovable Clone similarly centralizes errors in `error/GlobalExceptionHandler.java`, returns `ApiError`, and logs server-side exception context. Its DTOs are records, for example `dto/auth/AuthResponse.java` and `dto/project/ProjectResponse.java`.

AssetSphere keeps its existing `ApiResponse`, `ErrorResponse`, and `GlobalExceptionHandler`, retaining field-level validation output and correlation IDs. New API DTOs must be records unless a framework constraint requires a class. Controllers must delegate, never contain persistence or provider selection logic.

## Persistence, transactions, and mapping

Razorpay uses JPA entities, repositories, a shared audited `BaseEntity`, explicit `@Transactional` service methods, and `@Transactional(readOnly = true)` class defaults for read services. Evidence: `common/entity/BaseEntity.java`, `payment/service/impl/OrderServiceImpl.java`, `merchant/service/impl/ApiKeyServiceImpl.java`.

Lovable Clone uses entity/repository/MapStruct layers and applies transactions to write service methods. Evidence: `service/impl/ProjectServiceImpl.java`, `service/impl/ProjectMemberServiceImpl.java`, `mapper/ProjectMapper.java`.

AssetSphere retains UUIDs, UTC `Instant` auditing, and optimistic locking in `modules/common/BaseEntity`. Transactions belong at application-service use-case boundaries. Repositories must not publish events or select infrastructure providers.

## Security conventions

Razorpay has separate stateless filter chains for JWT routes and API-key routes and places idempotency after authentication. Evidence: `merchant/security/WebSecurityConfig.java`, `merchant/security/JwtAuthenticationFilter.java`, `merchant/security/ApiKeyAuthenticationFilter.java`.

Lovable Clone uses a single stateless JWT chain plus `@EnableMethodSecurity` and a `SecurityExpressions` bean for ownership checks. Evidence: `security/WebSecurityConfig.java`, `security/SecurityExpressions.java`, `service/impl/ProjectMemberServiceImpl.java`.

AssetSphere will use one stateless JWT chain and method-level workspace authorization when Auth and Workspace exist. API keys are deferred to a clearly defined machine-to-machine use case; they are not a general replacement for JWT. The current bootstrap denies unimplemented API routes by default.

## Razorpay-specific pattern analysis

| Pattern | Actual implementation | Adaptation decision |
|---|---|---|
| Idempotency | `IdempotencyFilter` claims Redis keys with `setIfAbsent`, stores successful response bodies, and replays them; `RedisIdempotencyStore` is interface-backed. | Adopt lifecycle and scoped key concept for write APIs. Defer implementation until a concrete workspace/asset mutation is present. AssetSphere must fail closed for operations whose audit/idempotency guarantees are mandatory. |
| Outbox | `payment/entity/OutboxEvent.java`, `payment/outbox/OutboxEventPublisher.java`, and scheduled `OutboxPoller.java` persist an event then publish it to Kafka. | Adopt transactional persistence plus scheduled publication, retry count, and terminal failure state. Defer to Processing because the technical design assigns outbox management there. |
| Retry/DLQ | Webhook retry queue/scheduler/DLQ types and Kafka consumer work are under `operations/webhook`. | Adopt retry policy and dead-letter record for processing events, not webhook-specific code. |
| Rate limiting | `RateLimiter` interface with fixed, token-bucket, and atomic Lua sliding-window implementations selected by property. | Adopt interface and namespaced Redis keys when authenticated API endpoints exist. Defer the filter because no API operation policy exists yet. |
| Provider routing | `PaymentAdapter`, `PaymentGatewayRouter`, `PaymentProcessorRouter`, and strategy implementations avoid provider conditionals in services. | Adopt for storage, intelligence, and notification providers. Existing `ObjectStorage` is aligned; future providers are additions, not service edits. |
| Caching | `ApiKeyCache` interface and Redis implementation isolate cache detail. | Adopt explicit cache ports for security/context data, instead of scattering cache annotations across domain services. |

## Lovable Clone-specific pattern analysis

| Pattern | Actual implementation | Adaptation decision |
|---|---|---|
| Method security | Security expressions applied with `@PreAuthorize` at service methods. | Adopt for workspace ownership/member checks after authentication exists. |
| Payment-provider boundary | `PaymentProcessor` and `StripePaymentProcessor` separate subscription service from Stripe API. | The pattern is valid; apply its provider boundary style to Storage and Intelligence, not Stripe. |
| DTO/mapper organization | Feature DTO packages and MapStruct mapper interfaces. | Adopt records and module-local mappers for actual AssetSphere features. |
| MinIO/AI/RAG claims | No related dependencies or working implementation were found; `FileServiceImpl` returns empty/null. | Explicitly reject as a source of implementation guidance. AssetSphere follows its own documented MinIO and AI architecture. |

## Spring Modulith findings

Neither reference contains `@ApplicationModule`, `@NamedInterface`, `@Modulithic`, `package-info.java` module declarations, or module-boundary tests. AssetSphere retains Spring Modulith because its own technical design requires a modular monolith. Module verification will use `ApplicationModules.of(AssetSphereApplication.class).verify()` and module declarations will be added only as modules gain implemented public APIs.

## Patterns rejected or deferred

- Inline database, JWT, vault, payment, or third-party secrets in YAML: rejected; AssetSphere requires environment variables and ignores `.env`.
- `ddl-auto: update`: rejected; AssetSphere uses Flyway and `validate`.
- Copying Razorpay's merchant/payment state machine or Lovable's billing model: rejected as domain-inappropriate.
- Failing open for compliance-critical audit/idempotency paths: rejected; availability policy must be decided per use case.
- AI, RAG, MinIO, Stripe, or tool-calling code inferred from Lovable Clone: deferred/rejected because no actual reference implementation exists.
- Generic outbox/idempotency/rate-limit tables or filters before a real write operation: deferred to the owning business module to avoid speculative infrastructure.

## AssetSphere gap and adaptation map

1. Add and enforce a testable Spring Modulith boundary baseline now.
2. Standardize event, topic, Redis-key, transaction, and provider conventions now in `06_ENGINEERING_CONVENTIONS.md`.
3. Implement transactional outbox, idempotency, distributed locking, and rate limiting with the first Asset or Workspace write use case.
4. Implement JWT and workspace authorization with Auth/Workspace, then use method security for ownership decisions.
5. Implement MinIO/S3 provider selection with Storage; implement AI providers, RAG, vector storage, and tool calling only with Intelligence/Search use cases.
