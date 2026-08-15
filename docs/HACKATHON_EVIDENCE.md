# Hackathon Engineering Evidence

| Concept | Implementation evidence |
|---|---|
| Modular monolith | Module `package-info.java` boundaries and Spring Modulith verification |
| Workspace isolation | `WorkspaceAccessFacade` checks across Assets, Search, Ask, Activity, Billing, and Intelligence APIs |
| Idempotency | `AssetIdempotencyService`, upload fingerprints, and workspace/idempotency uniqueness for billing payments |
| Transactional outbox | Asset upload/version transactions and the outbox publisher/repository |
| Kafka reliability | Processing, Intelligence, and Semantic listeners with bounded retry, DLT metadata, root-cause logging, and identity-preserving operator replay |
| Distributed locking | Redis locks around processing, intelligence, and semantic indexing |
| Caching | Redis-backed provider/payment-status caching and bounded refresh behavior |
| Rate limiting | Redis upload, semantic-search, and RAG limiters |
| Search | `AssetSearchDocumentRepository`, pgvector semantic retrieval, and `SearchApplicationService` hybrid RRF |
| Grounded RAG | Search evidence API, `WorkspaceQuestionAnsweringModel`, citation validation, and deterministic no-evidence behavior |
| Multimodal extraction | Document extractors, image OCR, and MP4/WebM transcription feeding bounded `AssetTextContent` |
| Bounded AI | Source/context limits, exact-version scope, result sanitizers, model entitlements, and provider-neutral ports |
| Version concurrency | Explicit append-version transaction, asset locking, and latest-version search behavior |
| Auditability | Security-context JPA auditor, reserved system UUID, `AuditService`, `audit_records`, and workspace activity API |
| Quotas | `BillingService`, centralized plan properties, and atomic period usage counters |
| Payment security | Stripe Checkout, verified webhook signatures, provider-event ordering, idempotent subscription synchronization, and cancel-at-period-end handling |
| Operational health | Actuator aggregate health, readiness, and liveness configuration |

## Key APIs

- Assets and versions: `/api/v1/workspaces/{workspaceId}/assets` and `/assets/{assetId}/versions`
- Search and grounded Ask: `/api/v1/workspaces/{workspaceId}/search` and `/ask`
- Exact-version Intelligence and Evolution: `/assets/{assetId}/versions/{versionNumber}/intelligence` and `/assets/{assetId}/compare`
- Collaboration and audit: workspace invitations, members, and `/api/v1/workspaces/{workspaceId}/activity`
- Billing: `/api/v1/workspaces/{workspaceId}/billing`, `/billing/checkout`, and `/billing/cancel`
- Stripe lifecycle: `/api/v1/billing/webhooks/stripe` is the provider-authoritative subscription callback
- Operations: opt-in `/api/v1/workspaces/{workspaceId}/ops/dlt` inspection/replay for authorized operators

Local Razorpay adapters are development/demo/reference integrations only. The deployed hackathon payment path is Stripe TEST/SANDBOX, and browser checkout completion never grants PRO without verified backend provider state.

## Production Verification

- The frontend, backend, Swagger UI, and health endpoint are publicly deployed over HTTPS.
- Google OAuth; workspace invitation through Resend and acceptance; OWNER/MEMBER authorization; asset upload, download, and versioning were exercised successfully.
- Document extraction, image OCR, MP4/WebM transcription, lexical/semantic/hybrid search, grounded Ask with citations, AI Intelligence, and Evolution Intelligence were exercised successfully.
- Stripe TEST Checkout, subscription synchronization, provider-event ordering, period handling, and cancel-at-period-end scheduling were verified while PRO access remained active.
- A FREE-plan OCR entitlement denial retried through Kafka and reached its DLT. This demonstrates that failure path, not exhaustive operator replay coverage.
- PostgreSQL-backed billing integration tests were separately verified.
- The full backend suite completed with 313 tests, 0 failures, 0 errors, and 6 environment-dependent skipped tests.

Terminal cancellation at the future renewal boundary has not been claimed as observed.
