# AssetSphere 4-Minute Demo Script

Use prepared assets and completed processing states wherever possible. Keep a second browser tab open on the README or Swagger UI so the recording never depends on a slow provider response.

## 0:00-0:25 — Problem and Architecture

**Screen / action:** Open the public landing page, then briefly point to the product headline and feature cards.

**Narration:** “Teams do not just store files — they need to understand knowledge that changes across documents, images, videos, and versions. AssetSphere is an event-driven multimodal knowledge and asset intelligence platform. A React client talks to a Spring Modulith backend, while PostgreSQL with pgvector, Redis, Kafka, private object storage, and OpenAI power retrieval and asynchronous intelligence.”

## 0:25-1:05 — Multimodal Assets and Async Processing

**Screen / action:** Sign in to a prepared workspace. Open Assets and show one READY document, one OCR-processed image, and one transcribed MP4 or WebM. Open an asset detail page and its version history. If time permits, open the upload dialog without submitting a new file.

**Narration:** “A workspace can hold documents, images through OCR, and MP4 or WebM video through transcription. Uploads return quickly, then move honestly through asynchronous processing states. Behind this screen, the upload transaction writes an outbox event, Kafka drives bounded extraction and indexing, and Redis locks prevent duplicate concurrent work. Every asset keeps explicit versions and exact-version downloads.”

## 1:05-1:45 — Hybrid Search and Grounded Ask

**Screen / action:** Run a prepared Hybrid Search query and open a relevant result. Move to Ask AssetSphere, submit a prepared question, and open one returned citation.

**Narration:** “Hybrid Search combines PostgreSQL lexical ranking with pgvector semantic similarity using deterministic reciprocal-rank fusion. Ask AssetSphere reuses that retrieval path instead of duplicating search logic. The model receives only bounded authorized evidence, while AssetSphere assigns trusted source IDs and builds citation metadata itself. Unknown citations are removed, and no evidence means no provider call.”

## 1:45-2:15 — Versioning and Evolution Intelligence

**Screen / action:** Return to an asset with at least two prepared versions. Show Version 1 and Version 2, then open a completed Evolution Intelligence comparison.

**Narration:** “Versioning is explicit: uploading a new version preserves the logical asset, assigns the next number safely, and reuses the full processing pipeline. Evolution Intelligence compares two exact authorized versions and highlights what changed, what was added, and what may need attention — without mixing unrelated workspace data.”

## 2:15-2:40 — Collaboration and RBAC

**Screen / action:** Open Members and Activity. Show OWNER and MEMBER roles plus one invitation or accepted invitation event.

**Narration:** “AssetSphere is workspace-first. Invitations are single-use and email-bound, production delivery uses Resend, and every workspace route authorizes membership before data access or provider calls. OWNER and MEMBER permissions are enforced by the backend, while the activity timeline records meaningful changes.”

## 2:40-3:10 — SaaS Billing and Stripe

**Screen / action:** Open Billing on a prepared workspace. Show the current plan, real usage bars, quotas, and Stripe subscription state. Do not start a new checkout during recording.

**Narration:** “Plans and quotas are backend-authoritative and checked before expensive AI operations. The deployed hackathon path uses Stripe TEST Checkout. Prices come from server configuration, and only verified Stripe webhooks can activate or synchronize PRO — the browser redirect never grants access. Cancel-at-period-end keeps PRO active through the current period.”

## 3:10-3:45 — Backend Engineering Proof

**Screen / action:** Switch to the README engineering table, then the Swagger UI. Scroll through the documented API groups and health link.

**Narration:** “The implementation emphasizes production backend concepts: idempotency protects retried uploads and checkouts; a transactional outbox closes the database-to-Kafka gap; bounded retries and DLTs preserve failures; Redis provides caching, rate limiting, and distributed locks; PostgreSQL and pgvector support hybrid retrieval; and Spring Modulith tests protect module boundaries. The verified backend suite contains 313 tests with zero failures and zero errors, with six environment-dependent tests skipped.”

## 3:45-4:00 — Close

**Screen / action:** Return to the landing-page headline or the completed Evolution Intelligence result.

**Narration:** “AssetSphere turns changing multimodal assets into secure, searchable, version-aware knowledge — with grounded AI and the reliability controls expected from a real SaaS backend. Your knowledge changes. AssetSphere understands what changed.”
