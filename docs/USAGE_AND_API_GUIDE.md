# Using AssetSphere — Web Application and API Guide

This guide covers the principal AssetSphere workflows through both the web application and Swagger. Examples use placeholders only; never publish real credentials, tokens, invitation links, or payment secrets.

- **Live application:** https://assetsphere-mu.vercel.app
- **Production Swagger:** https://assetsphere-production.up.railway.app/swagger-ui/index.html
- **Production API base:** `https://assetsphere-production.up.railway.app/api/v1`
- **Local Swagger entrypoint:** http://localhost:8080/swagger-ui.html
- **Local API base:** `http://localhost:8080/api/v1`

## Web Application Walkthrough

### Authentication

**Where to go:** Open the public application and choose **Get started**, **Sign in**, or **Continue with Google**.

**What to do:** Register with an unused email, sign in with an existing account, or use Google OAuth when enabled.

**What to expect:** A successful session routes to the workspace list or the current workspace. Authentication uses backend-issued tokens; the frontend does not infer membership or role authority.

### Workspace

**Where to go:** Use the workspace selector or **Create workspace**.

**What to do:** Open the default workspace created at registration or create an isolated workspace with a name, slug, and optional description.

**What to expect:** Navigation exposes the workspace features allowed for the current membership. Data remains scoped to the selected workspace.

### Upload

**Where to go:** Open **Assets** and choose **Upload asset**.

**What to do:** Select or drop a supported document, image, or video. Supported formats are PDF, DOCX, TXT, Markdown, CSV, JSON, XLSX, PPTX, PNG, JPEG/JPG, WebP, MP4, and WebM.

**What to expect:** The dialog validates the file, shows progress, and creates a new logical asset with Version 1. The frontend supplies an idempotency key so an identical retry does not create another asset.

### Processing

**Where to go:** Watch the Assets list or open the asset detail page.

**What to do:** Refetch or wait while the backend extracts content, builds indexes, and runs configured intelligence processing.

**What to expect:** Status can move through `UPLOADED`, `QUEUED`, `PROCESSING`, `READY`, `PARTIALLY_PROCESSED`, or `FAILED`. Upload acceptance is durable intent, not a promise that asynchronous processing has already completed.

Document extraction supports all listed document formats. Image OCR and video transcription depend on the active plan and configured providers.

### Asset Details and Download

**Where to go:** Select an asset from **Assets**.

**What to do:** Review display metadata, filename, MIME type, size, current version, processing status, and version history. Use **Download current version** or a version-row download action.

**What to expect:** Downloads return the exact authorized version with its stored filename and MIME type. Private object storage is never exposed as a public bucket URL.

### Search Modes

**Where to go:** Open **Search**.

**What to do:** Search the same phrase in **Lexical**, **Semantic**, and **Hybrid** modes.

**What to expect:** Lexical search favors exact words and phrases. Semantic search favors meaning-related content. Hybrid search combines both candidate rankings through deterministic Reciprocal Rank Fusion. Results refer to the latest version and expose version identity when available.

Semantic search requires the embedding provider and is Redis rate-limited. Results depend on completed indexing and the actual uploaded content.

### Ask and Citations

**Where to go:** Open **Ask AssetSphere**.

**What to do:** Ask a question that can be answered from workspace content.

**What to expect:** AssetSphere retrieves bounded hybrid evidence, generates a grounded answer, and shows trusted source cards with application-owned metadata. Unknown or duplicate model citation IDs are filtered.

If no evidence is available, the response is `I couldn't find enough information in this workspace to answer that.` with no citations and no model call.

### Intelligence

**Where to go:** Open an asset detail page and find **AI intelligence**. Workspace and asset insight actions are also available under **Insights**.

**What to do:** Choose the plan default or an available model, then explicitly generate intelligence for a processed exact version. You can also request grounded insight types or a knowledge check.

**What to expect:** The UI shows pending, generating, available, unavailable, or failed states honestly. Exact-version intelligence returns a summary, key points, and tags. Generation is quota-controlled and model selection is validated by the backend.

### Version History

**Where to go:** Open asset detail and choose **Upload new version**.

**What to do:** Upload the next revision of the same logical asset. This explicit action determines the version relationship; matching filenames or checksums do not automatically append versions.

**What to expect:** The asset ID remains stable, `latestVersionNumber` advances atomically, and each immutable version has its own file metadata and processing state. Version history is displayed newest first and marks the latest version.

### Evolution

**Where to go:** Open **Compare versions** on an asset with at least two versions.

**What to do:** Select different From and To versions and explicitly choose **Compare Versions**.

**What to expect:** Evolution Intelligence returns a structured executive summary, changes, additions, removals, and important differences. Changing a selection clears stale output but does not automatically call the provider.

### Collaboration

**Where to go:** Open **Members**.

**What to do:** An `OWNER` or `ADMIN` can invite a recipient with an allowed role. Only an `OWNER` may grant `OWNER`. The recipient accepts the single-use, expiring invitation with the intended email identity.

**What to expect:** Member data and controls reflect `OWNER`, `ADMIN`, `MEMBER`, `VIEWER`, and `AUDITOR` authorization. Email delivery can use Resend or SMTP; the authorized manual-copy flow remains available when delivery is disabled or fails.

### Activity

**Where to go:** Open the workspace overview.

**What to do:** Review recent workspace activity.

**What to expect:** Entries show durable business actions, actors, resources, and timestamps. Activity is separate from application diagnostic logs and does not expose document content.

### Billing

**Where to go:** Open **Billing & plan** as the workspace owner.

**What to do:** Review the plan catalog, usage, remaining allowances, current subscription state, renewal period, and cancellation state. You may open Stripe TEST/SANDBOX Checkout from the public deployment.

**What to expect:** The backend is authoritative for entitlements and usage. Returning from Checkout with a success query does not activate PRO; the page refetches billing state and waits for a verified provider event. Cancel-at-period-end keeps paid access until the authoritative period end.

### OCR and Video

**Where to go:** Upload or open a PNG/JPEG/WebP image or MP4/WebM video in an entitled workspace.

**What to do:** Wait for processing, then search, ask, or generate exact-version intelligence from visible text or spoken content.

**What to expect:** OCR and transcription feed the same bounded text, lexical, semantic, Ask, and intelligence paths as documents. Unsupported or unavailable provider behavior becomes an honest processing or entitlement state.

## Swagger / API Walkthrough

### API Conventions

Responses normally use this envelope:

```json
{
  "success": true,
  "data": {},
  "timestamp": "<UTC_TIMESTAMP>",
  "correlationId": "<CORRELATION_ID_OR_NULL>"
}
```

The OpenAPI scheme is HTTP `bearer` with JWT format. After login, copy `data.accessToken`, click **Authorize**, and paste the **raw token only** (`<JWT>`). Swagger adds the `Bearer` prefix.

Use unique placeholders such as `<WORKSPACE_ID>`, `<ASSET_ID>`, `<ASSET_VERSION_ID>`, and `<PASSWORD>` throughout this workflow.

### 1. Register

- **Method/route:** `POST /api/v1/auth/register`
- **Authorization:** Public
- **Content type:** `application/json`
- **Body:**

```json
{
  "email": "developer@example.com",
  "password": "<PASSWORD_WITH_UPPER_LOWER_AND_DIGIT_12+>",
  "displayName": "AssetSphere Developer"
}
```

- **Expected:** `201 Created`
- **Reuse:** `data.defaultWorkspace.id` can become `<WORKSPACE_ID>`.
- **Note:** Registration does not return tokens; log in separately.

### 2. Login

- **Method/route:** `POST /api/v1/auth/login`
- **Authorization:** Public
- **Body:**

```json
{
  "email": "developer@example.com",
  "password": "<PASSWORD>"
}
```

- **Expected:** `200 OK`
- **Reuse:** Copy `data.accessToken` as `<JWT>`. The response also includes refresh-token and expiry fields.

### 3. Retrieve Current Identity

- **Method/route:** `GET /api/v1/auth/me`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`
- **Reuse:** Copy a workspace ID and inspect the associated role. `GET /api/v1/auth/providers` reports whether Google OAuth is enabled.

### 4. Create a Workspace

- **Method/route:** `POST /api/v1/workspaces`
- **Authorization:** Bearer JWT
- **Body:**

```json
{
  "name": "Knowledge Workspace",
  "slug": "knowledge-<UNIQUE_SUFFIX>",
  "description": "Workspace for AssetSphere exploration"
}
```

- **Expected:** `201 Created`
- **Reuse:** Copy `data.id` as `<WORKSPACE_ID>`.

### 5. List or Get Workspaces

- **Routes:** `GET /api/v1/workspaces` and `GET /api/v1/workspaces/<WORKSPACE_ID>`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`
- **Result:** The list is membership-filtered; detail includes ID, name, slug, description, and status.

### 6. Upload an Asset

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/assets`
- **Authorization:** Bearer JWT
- **Required header:** `Idempotency-Key: asset-upload-001`
- **Body:** `multipart/form-data` with required `file` and optional `displayName` and `description`. Let Swagger set the boundary.
- **Expected:** `201 Created`
- **Reuse:** Copy `data.assetId`, `data.assetVersionId`, and `data.versionNumber`.
- **Result:** The response includes processing status, MIME/type, checksum, and `X-Idempotent-Replay: false`.
- **Idempotency:** Repeat the identical request with the same key to receive a replay and `X-Idempotent-Replay: true`. Change file or metadata under the same key to receive a conflict.

### 7. List and Get an Asset

- **Routes:** `GET /api/v1/workspaces/<WORKSPACE_ID>/assets?page=0&size=20` and `GET /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`
- **Result:** Asset detail includes IDs, current version number, metadata, and processing status. Poll this existing endpoint until `READY`, `PARTIALLY_PROCESSED`, or a terminal `FAILED` state.

### 8. List Version History

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`
- **Result:** Each row contains asset version ID, version number, filename, MIME type, size, processing status, and timestamp.

### 9. Download an Exact Version

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions/1/download`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK` with attachment content disposition, MIME type, and content length.

### 10. Lexical Search

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/search?q=remote%20work%20policy&mode=LEXICAL&page=0&size=20`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`; exact words and phrases are favored.

### 11. Semantic Search

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/search?q=remote%20work%20policy&mode=SEMANTIC&page=0&size=20`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`; meaning-related content can match without identical wording.
- **Note:** Semantic search requires a configured embedding provider and is Redis rate-limited.

### 12. Hybrid Search

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/search?q=remote%20work%20policy&mode=HYBRID&page=0&size=20`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK`; lexical and semantic candidate ranks are combined through deterministic RRF.

Search queries are bounded to 1–200 characters. `page` starts at `0`, and `size` is bounded. Results include asset/version identity, version number, filename/display name, MIME type, status, rank, and snippet.

### 13. Ask AssetSphere

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/ask`
- **Authorization:** Bearer JWT
- **Body:**

```json
{
  "question": "What does the remote work policy allow?",
  "modelId": null
}
```

- **Expected:** `200 OK` when provider and quota are available.
- **Result:** `data.answer` and `data.citations[]` with trusted source ID, asset/version IDs, title/filename, optional chunk ordinal, and snippet.
- **No evidence:** The deterministic insufficient-evidence answer is returned with `citations: []` and no model invocation.

### 14. Generate and Read Exact-Version Intelligence

- **Generate:** `POST /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions/1/intelligence/generate`
- **Read:** `GET /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions/1/intelligence`
- **Authorization:** Bearer JWT
- **Body:** `{}` for the plan default model, or `{ "modelId": "<AVAILABLE_MODEL_ID>" }`.
- **Expected:** `200 OK` when the version is processable and quota/provider are available.
- **Result:** Status, exact asset version ID, summary, key points, tags, provider/model, input truncation flag, and generation time.

The authorized model catalog is available from `GET /api/v1/workspaces/<WORKSPACE_ID>/ai/models`.

Additional grounded AI routes include:

- `POST /api/v1/workspaces/<WORKSPACE_ID>/insights`
- `POST /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions/<VERSION_NUMBER>/insights`
- `POST /api/v1/workspaces/<WORKSPACE_ID>/quiz`
- `POST /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions/<VERSION_NUMBER>/quiz`

Insight requests accept `type`, optional `focus`, and optional `modelId`. Supported types are `EXECUTIVE_BRIEF`, `KEY_DECISIONS`, `RISKS_AND_GAPS`, `ACTION_ITEMS`, `OPEN_QUESTIONS`, `CONTRADICTIONS`, and `KNOWLEDGE_CHECK`.

Quiz requests accept `questionCount`, `difficulty` (`EASY`, `MEDIUM`, or `HARD`), optional `topic`, and optional `modelId`. These routes require explicit invocation and applicable workspace authorization, plan, quota, and model entitlement.

### 15. Upload a New Version

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions`
- **Authorization:** Bearer JWT
- **Required header:** `Idempotency-Key: asset-version-002`
- **Body:** `multipart/form-data` with required `file` only.
- **Expected:** `201 Created`
- **Result:** Asset ID is unchanged; copy the new asset version ID and version number.
- **Idempotency:** The same file/key replays; changed content with the same key conflicts.

### 16. Re-list Versions

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/versions`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK` with both versions and independent processing states.

### 17. Compare Versions

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/assets/<ASSET_ID>/compare`
- **Authorization:** Bearer JWT
- **Body:**

```json
{
  "fromVersion": 1,
  "toVersion": 2,
  "modelId": null
}
```

- **Expected:** `200 OK` when both versions are processed and quota/provider are available.
- **Result:** From/to version, executive summary, key changes, additions, removals, and important changes.

### 18. Invite a Member

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/invitations`
- **Authorization:** Bearer JWT as `OWNER` or `ADMIN`
- **Body:**

```json
{
  "email": "<INVITEE_EMAIL>",
  "role": "MEMBER"
}
```

- **Expected:** `201 Created`
- **Result:** Invitation identity, recipient, role, expiry, single-use acceptance URL/token, and `emailDeliveryStatus` (`SENT`, `DISABLED`, or `FAILED`). Do not publish the returned token or link.

### 19. List Members

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/members`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK` with member/user IDs, display name/email, role, status, and join time.

### 20. Read Workspace Activity

- **Method/route:** `GET /api/v1/workspaces/<WORKSPACE_ID>/activity?page=0&size=20`
- **Authorization:** Bearer JWT
- **Expected:** `200 OK` with bounded audit ID, actor, action, resource type/ID, and occurrence time.

### 21. Read Plans, Capabilities, and Billing

- **Routes:** `GET /api/v1/billing/plans`, `GET /api/v1/billing/payment-capabilities`, and `GET /api/v1/workspaces/<WORKSPACE_ID>/billing`
- **Authorization:** Bearer JWT for workspace billing
- **Expected:** `200 OK`
- **Result:** Plan entitlements, provider capabilities, current plan/status, usage, remaining allowances, period, payment state, provider, renewal, and cancellation scheduling.

### 22. Create Stripe TEST/SANDBOX Checkout

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/billing/checkout`
- **Authorization:** Bearer JWT as `OWNER`
- **Required header:** `Idempotency-Key: billing-checkout-001`
- **Body:** None; amount and plan price are backend-owned.
- **Expected:** `200 OK`
- **Result:** Provider, provider session/order ID, hosted checkout URL, hosted-checkout capability, amount in minor units, currency, and payment status.
- **Note:** Completing or abandoning the browser flow is not subscription authority. Refetch workspace billing for verified state.

### 23. Schedule Cancel at Period End

- **Method/route:** `POST /api/v1/workspaces/<WORKSPACE_ID>/billing/cancel`
- **Authorization:** Bearer JWT as `OWNER`
- **Body:** None
- **Expected:** `200 OK` for an active cancellable Stripe subscription.
- **Result:** A billing read can show `cancelAtPeriodEnd: true`; PRO remains active until the authoritative period end.

## Provider Callback Endpoints

`POST /api/v1/billing/webhooks/stripe` is called by Stripe, not manually during the user workflow. The adapter verifies the Stripe signature, billing claims the provider event idempotently, and confirmation ties the provider identity to the existing payment and workspace before changing subscription state. Browser redirects cannot grant PRO.

## Operational APIs

When `ASSETSPHERE_DLT_OPS_ENABLED=true`, authorized `OWNER`/`ADMIN` operators can use:

- `GET /api/v1/workspaces/<WORKSPACE_ID>/ops/dlt?limit=50`
- `POST /api/v1/workspaces/<WORKSPACE_ID>/ops/dlt/<TOPIC>/<PARTITION>/<OFFSET>/replay`

The controller may be absent from Swagger when disabled. Replay validates that the selected record belongs to the path workspace and republishes the unchanged event identity after feature-specific failed state is prepared.

## API Concepts Worth Exploring

- **Safe request replay:** Repeat an upload with the same `Idempotency-Key` and identical request to receive the original result rather than another asset.
- **Fingerprint conflict:** Reuse that key with changed content or metadata to receive a conflict instead of silently accepting a different operation.
- **Durable acceptance vs completion:** Observe the asset response immediately after upload and then poll its existing detail route until processing reaches a terminal state.
- **Retrieval behavior:** Run the same query through lexical, semantic, and hybrid modes to compare exact-term, meaning-based, and fused rankings.
- **Exact-version scope:** Append Version 2, download both versions, generate intelligence for a selected version, and compare explicit version numbers.
- **Authorization boundaries:** Compare controls and API outcomes across `OWNER`, `ADMIN`, and non-mutating roles; UI visibility is not the security boundary.
- **Backend-authoritative billing:** Return from hosted Checkout and confirm that only the subsequently refetched, provider-verified billing state controls entitlements.
