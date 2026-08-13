# Hackathon Engineering Evidence

| Concept | Implementation evidence |
|---|---|
| Modular monolith | Module `package-info.java` boundaries and Spring Modulith tests |
| Workspace isolation | `WorkspaceAccessFacade` checks in Assets, Search, Ask, Activity, Billing, and Intelligence APIs |
| Idempotency | `AssetIdempotencyService`, upload fingerprints, `billing_payments` workspace/idempotency uniqueness |
| Transactional outbox | Asset upload/version transactions and outbox publisher/repository |
| Kafka reliability | Processing/Intelligence/Semantic listeners, bounded exponential retry, DLT metadata inspection and identity-preserving manual replay |
| Distributed locking | Redis processing, intelligence, and semantic indexing locks |
| Rate limiting | Redis upload, semantic-search, and RAG limiters |
| Search | `AssetSearchDocumentRepository`, pgvector semantic retrieval, `SearchApplicationService` hybrid RRF |
| Grounded RAG | Search evidence API, `WorkspaceQuestionAnsweringModel`, citation validation and no-evidence path |
| Bounded AI | Extraction limits, source/context limits, result sanitizers, provider-neutral ports |
| Version concurrency | Explicit append-version transaction and asset version locking |
| Auditability | Security-context JPA auditor, explicit reserved system UUID, `AuditService`, `audit_records`, workspace activity API |
| Quotas | `BillingService`, centralized `BillingProperties`, atomic monthly usage counters |
| Payment security | Stripe/local `PaymentGateway` adapters, verified HMAC webhooks, one-payment-per-attempt state, and idempotent activation |
| Operational health | Actuator health/readiness/liveness configuration |
| Responsive readiness | Mobile drawer navigation, dynamic viewport/safe-area handling, bounded dialogs, touch targets, request-ID and clipboard fallbacks |

## Key APIs

- `/api/v1/workspaces/{workspaceId}/assets` and `/assets/{assetId}/versions`
- `/api/v1/workspaces/{workspaceId}/search` and `/ask`
- Exact-version Intelligence and `/assets/{assetId}/compare`
- `/api/v1/workspaces/{workspaceId}/activity`
- `/api/v1/workspaces/{workspaceId}/billing` and `/billing/checkout`
- opt-in `/api/v1/workspaces/{workspaceId}/ops/dlt` inspection/replay for workspace operators
- `/api/v1/billing/webhooks/razorpay` (provider callback; not a client success API)

V16 normalizes historical `RAZORPAY` rows and aligns payment provider/status constraints with current enums. Search is latest-version-only. DLT replay now prepares failed asset/semantic state before republishing. These lock changes are statically implemented and require external test/build plus runtime verification before being claimed as verified.
