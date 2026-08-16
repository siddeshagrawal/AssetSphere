# Hackathon Engineering Evidence

This is the judge's fast path: **hackathon requirement → AssetSphere implementation → concrete classes/config → verification**.

## Requirements and Evidence

| Requirement | AssetSphere implementation | Concrete classes/config | Verification |
|---|---|---|---|
| Spring Boot core backend | Java 21 REST backend with Security, validation, JPA/JDBC, scheduling, Kafka, Redis, Actuator, and Flyway | `assetsphere-backend/pom.xml`, `modules/*/api/*Controller.java`, `modules/common/web/GlobalExceptionHandler.java` | Public Swagger and health endpoints; verified backend suite |
| Modular architecture | Spring Modulith modules declare allowed dependencies and expose named `api` interfaces | `modules/*/package-info.java`, `ModularityTests.java` | Spring Modulith verification included in the verified suite |
| Idempotency | Upload/version requests bind user, workspace, key, and request fingerprint; matching retries replay, changed requests conflict | `AssetIdempotencyService.java`, `UploadFingerprint.java`, `IdempotencyRecord.java`, `BillingPaymentTransaction.java` | Upload/version and billing application tests |
| Transactional outbox | Internal Spring events create durable outbox rows in the originating transaction | `OutboxApplicationService.java`, `OutboxEvent.java`, `OutboxEventRepository.java` | Outbox domain/application coverage in the verified suite |
| Outbox claiming | PostgreSQL atomically leases eligible rows and recovers stale claims | `OutboxEventClaimRepository.java`, `OutboxClaimingService.java`, `OutboxPublisher.java` | PostgreSQL-specific SQL inspected; repository/domain tests present |
| Publisher retry | Kafka send acknowledgement is awaited; failure schedules bounded exponential backoff or terminal outbox failure | `KafkaOutboxMessagePublisher.java`, `OutboxPublishingStateService.java`, `ProcessingProperties.java` | Unit coverage for outbox state; production async processing verified |
| Consumer retry | Topic-specific listener containers apply bounded backoff and non-retryable classification | `KafkaReliabilityConfiguration.java` | Production processing paths verified; FREE OCR denial exercised retries |
| Dead-letter topics | Exhausted processing/intelligence/semantic failures publish to dedicated DLTs with safe root-cause logging | `KafkaReliabilityConfiguration.java`, Kafka listener classes | FREE OCR entitlement denial reached DLT in production |
| Controlled DLT operations | Feature-gated OWNER/ADMIN inspection and identity-preserving replay | `DltOperationsController.java`, `DltOperationsService.java` | Code and authorization coverage; exhaustive replay is not claimed |
| Caching | Redis stores stable asset metadata snapshots, not entities or authorization state; write paths invalidate cache | `RedisAssetMetadataCache.java`, `AssetQueryService.java`, `AssetMetadataApplicationService.java`, `AssetUploadTransaction.java` | `RedisAssetMetadataCacheTests.java` |
| Rate limiting | Redis upload, semantic-search, and RAG limiters use workspace/user scopes | `RedisAssetUploadRateLimiter.java`, `RedisSemanticSearchRateLimiter.java`, `RedisRagRateLimiter.java` | Application wiring/tests in verified suite; no broader generic limiter claim |
| Distributed locking | Token-owned Redis locks coordinate processing, intelligence, and semantic indexing per asset version | `RedisAssetProcessingLock.java`, `RedisIntelligenceProcessingLock.java`, `RedisSemanticIndexingLock.java` | Listener/application tests cover lock use |
| Kafka | Outbox-published events drive extraction, semantic indexing, and intelligence separately from HTTP upload | `OutboxPublisher.java`, `KafkaOutboxMessagePublisher.java`, `AssetUploadedKafkaListener.java`, intelligence/semantic listeners | Multimodal processing and indexing verified in production |
| PostgreSQL database | Authoritative tenant, asset/version, search, billing, outbox, and audit state with JPA/JDBC and Flyway V1-V17 | `src/main/resources/db/migration/`, module persistence packages | PostgreSQL billing integration tests separately verified |
| pgvector | 1536-dimensional embeddings and `<=>` retrieval remain in PostgreSQL | `SemanticIndexingApplicationService.java`, `AssetContentChunkVectorRepository.java`, `AssetSearchDocumentRepository.java` | Semantic indexing and semantic/hybrid retrieval verified in production |
| Storage consistency | Workspace-scoped checksum dedup, temporary/canonical keys, atomic reference upsert, and compensation | `StorageApplicationService.java`, `AssetUploadService.java`, `AssetUploadTransaction.java` | `StorageApplicationServiceTests.java`, PostgreSQL storage integration test |
| Version concurrency | Explicit append-version locks the logical asset before incrementing `latestVersionNumber` | `AssetRepository.findForUpdate(...)`, `Asset.appendVersion(...)`, `AssetUploadTransaction.persistVersion(...)` | `AssetUploadTransactionVersionTests.java`; versioning verified in production |
| Workspace/RBAC isolation | Active membership/role checks precede workspace access and provider calls; queries retain workspace predicates | `WorkspaceAccessFacade.java`, `WorkspaceAuthorization.java`, workspace-scoped application services | OWNER/MEMBER behavior verified in production |
| JWT and OAuth | Spring Security, JWT filter, BCrypt, refresh rotation, and optional Google OAuth | `SecurityConfiguration.java`, `JwtAuthenticationFilter.java`, auth application classes | Password/JWT flow and Google OAuth verified in production |
| Audit trail | Durable business actions are separate from diagnostic logs and exposed through an authorized activity query | `AuditService.java`, `AuditRecord.java`, `WorkspaceActivityQuery.java`, `WorkspaceActivityController.java` | Workspace activity verified in production |
| Lexical search | PostgreSQL full-text ranking/snippets over workspace-scoped latest versions | `AssetSearchDocumentRepository.java`, `SearchApplicationService.java` | `SearchApplicationServiceTests.java`; production lexical search verified |
| Semantic search | Query embedding, vector dimension validation, pgvector similarity, and READY vector integrity | `EmbeddingModelPort.java`, `OpenAiDocumentEmbeddingModel.java`, semantic indexing classes | Semantic tests plus production semantic search verification |
| Hybrid search | Deterministic RRF merges lexical and semantic ranks without score calibration | `SearchApplicationService.hybrid(...)` | Search service tests; production hybrid search verified |
| Grounded Ask/RAG | Bounded Search evidence, deterministic `S1...`, trusted citations, unknown removal, deduplication, and no-model no-evidence path | `WorkspaceSearchEvidenceRetriever.java`, `WorkspaceRagApplicationService.java`, `WorkspaceQuestionAnsweringModel.java` | `WorkspaceRagApplicationServiceTests.java`; production cited Ask verified |
| Multimodal pipeline | Format-specific document extractors plus authorized image OCR and MP4/WebM transcription | `processing/text/*TextExtractor.java`, `OpenAiImageOcrProvider.java`, `OpenAiMediaTranscriptionProvider.java` | Documents, image OCR, and video transcription verified in production |
| AI provider boundaries | Application-owned model ports and sanitized business results isolate OpenAI/Spring AI details | `EmbeddingModelPort.java`, `WorkspaceQuestionAnsweringModel.java`, intelligence model interfaces and OpenAI adapters | Intelligence/model/sanitizer tests; production Intelligence verified |
| Exact-version/Evolution | Exact version metadata/content is selected explicitly; Evolution compares two authorized bounded versions | `AssetReadFacade.java`, `AssetEvolutionApplicationService.java`, `OpenAiDocumentIntelligenceModel.java` | Evolution parsing/application tests; production Evolution verified |
| Backend entitlements | FREE/PRO/ENTERPRISE plan limits and atomic usage consumption are backend-authoritative | `BillingService.java`, `BillingUsageRepository.java`, `BillingProperties.java`, `BillingEntitlementFacade.java` | Billing service/repository tests and production quota behavior |
| Stripe subscriptions | Backend-priced hosted Checkout, verified signatures, idempotent provider events, subscription identity/period synchronization, cancellation scheduling | `StripePaymentGateway.java`, `BillingWebhookService.java`, `ProviderPaymentConfirmationService.java`, `BillingWebhookRepository.java` | Stripe adapter/webhook tests plus production Stripe TEST flow |
| Payment abstraction | Billing core targets `PaymentGateway`; Stripe is production, local Razorpay is development-only and rejected in prod | `PaymentGateway.java`, `StripePaymentGateway.java`, `LocalRazorpayPaymentGateway.java`, production validators | Profile/payment tests in verified suite; deployed path is Stripe only |
| Invitation email | Provider-neutral invitation sender with Resend HTTPS and SMTP adapters; manual-copy flow remains available | `WorkspaceInvitationEmailSender.java`, `ResendWorkspaceInvitationEmailSender.java`, `SmtpWorkspaceInvitationEmailSender.java` | Resend delivery and invitation acceptance verified in production |
| Frontend | Typed React UI exposes real async states, search/Ask, assets/versions, collaboration, billing, and role-aware controls | `assetsphere-frontend/src/pages/`, `src/features/`, `src/api/`, `src/types/` | Production user journeys verified on Vercel |
| Deployment | Vercel + Railway with managed PostgreSQL/pgvector, hosted TLS Redis, Kafka/Redpanda SASL_SSL, and private S3-compatible storage | `docs/DEPLOYMENT.md`, profile YAML, environment templates | Public frontend/backend/health verified |
| Swagger/OpenAPI | Springdoc exposes the current REST contract | `pom.xml`, API controllers, Springdoc configuration | [Public Swagger UI](https://assetsphere-production.up.railway.app/swagger-ui/index.html) |
| Observability | Actuator health/readiness/liveness, Micrometer counters, correlation IDs, and safe terminal-failure logs | Actuator config, `KafkaReliabilityConfiguration.java`, `DltOperationsService.java`, `IntelligenceEventProcessor.java` | [Public health endpoint](https://assetsphere-production.up.railway.app/actuator/health) |

## Representative Source Map

- Module boundaries: `assetsphere-backend/src/main/java/com/assetsphere/modules/*/package-info.java`
- Upload idempotency: `assetsphere-backend/src/main/java/com/assetsphere/modules/asset/application/AssetIdempotencyService.java`
- Version transaction: `assetsphere-backend/src/main/java/com/assetsphere/modules/asset/application/AssetUploadTransaction.java`
- Storage consistency: `assetsphere-backend/src/main/java/com/assetsphere/modules/storage/application/StorageApplicationService.java`
- Transactional event creation: `assetsphere-backend/src/main/java/com/assetsphere/modules/processing/outbox/application/OutboxApplicationService.java`
- Outbox row lifecycle: `assetsphere-backend/src/main/java/com/assetsphere/modules/processing/outbox/domain/OutboxEvent.java`
- PostgreSQL claiming: `assetsphere-backend/src/main/java/com/assetsphere/modules/processing/outbox/persistence/OutboxEventClaimRepository.java`
- Claim lease service: `assetsphere-backend/src/main/java/com/assetsphere/modules/processing/outbox/application/OutboxClaimingService.java`
- Publication state/backoff: `assetsphere-backend/src/main/java/com/assetsphere/modules/processing/outbox/application/OutboxPublishingStateService.java`
- Kafka publisher adapter: `assetsphere-backend/src/main/java/com/assetsphere/infrastructure/kafka/KafkaOutboxMessagePublisher.java`
- Consumer retry/DLT: `assetsphere-backend/src/main/java/com/assetsphere/infrastructure/kafka/KafkaReliabilityConfiguration.java`
- Redis metadata cache: `assetsphere-backend/src/main/java/com/assetsphere/infrastructure/redis/RedisAssetMetadataCache.java`
- Search/RRF: `assetsphere-backend/src/main/java/com/assetsphere/modules/search/application/SearchApplicationService.java`
- PostgreSQL/pgvector retrieval: `assetsphere-backend/src/main/java/com/assetsphere/modules/search/persistence/AssetSearchDocumentRepository.java`
- Grounded RAG: `assetsphere-backend/src/main/java/com/assetsphere/modules/intelligence/application/WorkspaceRagApplicationService.java`
- Workspace authorization: `assetsphere-backend/src/main/java/com/assetsphere/modules/workspace/api/WorkspaceAccessFacade.java`
- Stripe adapter: `assetsphere-backend/src/main/java/com/assetsphere/infrastructure/payment/StripePaymentGateway.java`
- Verified webhook orchestration: `assetsphere-backend/src/main/java/com/assetsphere/modules/billing/application/BillingWebhookService.java`
- Provider/payment confirmation: `assetsphere-backend/src/main/java/com/assetsphere/modules/billing/application/ProviderPaymentConfirmationService.java`
- Resend transport: `assetsphere-backend/src/main/java/com/assetsphere/infrastructure/notification/ResendWorkspaceInvitationEmailSender.java`

## Reliability Evidence

### 1. Transaction-bound event creation

`AssetUploadTransaction` persists the asset/version/idempotency result and publishes `AssetUploadedEvent`. `OutboxApplicationService` receives that event through `@EventListener` and uses `@Transactional(propagation = Propagation.MANDATORY)`. The listener cannot silently start an unrelated transaction: its outbox insert participates in the original transaction and rolls back with it.

The same outbox service handles `AssetReadyForIntelligenceEvent` and `AssetReadyForSemanticIndexEvent`. Internal Spring events therefore capture transaction-local intent; serialized Kafka events are emitted only by the asynchronous publisher.

### 2. Concurrent publisher claiming

`OutboxEventClaimRepository` executes a PostgreSQL common-table expression that:

- selects due `PENDING` rows or stale `PROCESSING` rows;
- orders by creation time and ID;
- applies `FOR UPDATE SKIP LOCKED`;
- limits the batch;
- atomically updates owner, claim timestamp, status, and version;
- returns only the rows owned by that claim.

`OutboxClaimingService` supplies a per-process publisher identity and computes `staleBefore` from the configured lease duration. This lets multiple publishers share work without Redis becoming a second outbox authority.

### 3. Publication acknowledgement and retry

`KafkaOutboxMessagePublisher` calls `KafkaTemplate.send(...)` and awaits the result with a configured timeout. `OutboxPublishingStateService` marks the event published only after that acknowledgement. A failed attempt calculates exponential delay bounded by the configured maximum; exhausting the configured attempts marks the outbox row failed.

This publisher lifecycle is separate from Kafka consumer retries. A terminal outbox failure means Kafka publication could not be confirmed. A consumer DLT means Kafka received the event but downstream processing exhausted its attempts.

### 4. Consumer retry and DLT

`KafkaReliabilityConfiguration` owns listener error handling and `DeadLetterPublishingRecoverer` instances for asset processing, intelligence, and semantic indexing. Original exceptions are logged with stack traces and safe identifiers; document text, prompts, model output, vectors, and secrets are excluded.

`DltOperationsController` is disabled unless explicitly configured and requires an authorized workspace operator. Replay validates that the selected record belongs to the path workspace and republishes the unchanged event identity after feature-specific failed state is prepared.

### 5. Delivery semantics

AssetSphere is deliberately **at-least-once-safe and duplicate-tolerant**:

- client retries use idempotency records and fingerprints;
- outbox sends may be retried after uncertain acknowledgement;
- Kafka may redeliver;
- consumers use stable event identity, state checks, and distributed locks;
- version creation uses a database lock;
- storage uses checksum deduplication and compensation.

No exactly-once or distributed-transaction claim is made.

## Verified Product Evidence

- Public frontend: https://assetsphere-mu.vercel.app
- Public backend: https://assetsphere-production.up.railway.app
- Public Swagger: https://assetsphere-production.up.railway.app/swagger-ui/index.html
- Public health: https://assetsphere-production.up.railway.app/actuator/health
- Google OAuth works in production.
- Asset upload, private download, explicit versioning, and exact-version history work.
- PDF/DOCX/text/Markdown/CSV/JSON/XLSX/PPTX processing, image OCR, and MP4/WebM transcription work.
- Lexical, semantic, and hybrid search work.
- Grounded Ask returns citations; AI Intelligence and Evolution Intelligence work.
- Workspace invitation, Resend HTTPS delivery using the Resend test sender, acceptance, and OWNER/MEMBER authorization work.
- Stripe TEST/SANDBOX Checkout, subscription synchronization, provider-event ordering, period handling, and cancel-at-period-end scheduling were verified while access remained active.
- A FREE-plan OCR entitlement denial retried through Kafka and reached its DLT. This verifies that failure path, not exhaustive replay behavior.
- PostgreSQL-backed billing integration tests were separately verified.

Terminal Stripe cancellation at the future renewal boundary is not claimed as observed.

## Test Evidence

The last verified backend suite completed:

- **313 tests**
- **0 failures**
- **0 errors**
- **6 environment-dependent skipped tests**

Representative focused tests include:

- `ModularityTests.java`
- `SearchApplicationServiceTests.java`
- `SemanticIndexingApplicationServiceTests.java`
- `WorkspaceRagApplicationServiceTests.java`
- `AssetUploadTransactionVersionTests.java`
- `StorageApplicationServiceTests.java`
- `RedisAssetMetadataCacheTests.java`
- `StripePaymentGatewayTests.java`
- `BillingWebhookServiceTests.java`
- `BillingLifecycleRepositoryPostgresIntegrationTests.java`
- `BillingWebhookRepositoryPostgresIntegrationTests.java`

No new test execution is implied by this documentation-only pass.
