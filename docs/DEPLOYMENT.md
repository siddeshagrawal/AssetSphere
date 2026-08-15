# AssetSphere Deployment

AssetSphere uses explicit Spring profiles. Local development uses `dev`; hosted deployments must set `SPRING_PROFILES_ACTIVE=prod`. Store credentials in the deployment platform's secret manager, never in source, frontend variables, images, or logs.

## Deployed Hackathon Endpoints

- Frontend: https://assetsphere-mu.vercel.app
- Backend: https://assetsphere-production.up.railway.app
- Swagger: https://assetsphere-production.up.railway.app/swagger-ui/index.html
- Health: https://assetsphere-production.up.railway.app/actuator/health

## Environment Matrix

| Dependency / feature | Development | Production |
|---|---|---|
| Frontend | `http://localhost:5173` | Public HTTPS origin |
| Backend | `http://localhost:8080` | Public HTTPS origin behind a trusted proxy |
| PostgreSQL + pgvector | `localhost:5433` | External managed PostgreSQL with pgvector |
| Redis | `localhost:6379` | Hosted Redis with authentication and TLS |
| Kafka | `localhost:29092` | Hosted Kafka/Redpanda with SASL_SSL |
| Object storage | MinIO at `http://localhost:9000` | Private S3-compatible HTTPS storage; bucket provisioned externally |
| OpenAI | Optional and feature-gated | Production key with explicitly enabled capabilities |
| Google OAuth | Optional localhost redirects | Production client and HTTPS redirects |
| Invitation email | SMTP optional; manual-copy fallback | Resend HTTPS; hackathon deployment uses the Resend test sender; SMTP remains supported |
| Payments | Stripe test mode or `RAZORPAY_LOCAL` | Stripe only; hackathon deployment uses TEST/SANDBOX credentials |

## Required Production Configuration

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_URL`, or the supported Redis host/port/username/password/SSL properties
- `KAFKA_BOOTSTRAP_SERVERS` plus `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG`, and TLS endpoint identification for hosted clusters
- `ASSETSPHERE_JWT_SECRET`
- `ASSETSPHERE_CORS_ALLOWED_ORIGINS` and `FRONTEND_BASE_URL`, both using production HTTPS origins
- `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and `MINIO_BUCKET`; `MINIO_AUTO_CREATE_BUCKET=false`
- `ASSETSPHERE_PAYMENT_MODE=STRIPE`, `STRIPE_ENABLED=true`, `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`, and `STRIPE_PRO_PRICE_ID`

Production configuration must not contain production-critical localhost URLs. `RAZORPAY_LOCAL` and local payment polling/card-demo features are rejected in the production profile. Stripe mode validates required Stripe configuration at startup. Checkout return URLs are derived from `FRONTEND_BASE_URL` per workspace.

## Optional Feature-Gated Configuration

- Generative AI: `ASSETSPHERE_AI_ENABLED=true`, `OPENAI_API_KEY`, and chat model configuration
- Semantic embeddings: `ASSETSPHERE_AI_EMBEDDING_ENABLED=true`, `ASSETSPHERE_AI_SPRING_EMBEDDING_MODEL=openai`, `ASSETSPHERE_AI_EMBEDDING_MODEL=text-embedding-3-small`, dimension `1536`
- Video transcription: `ASSETSPHERE_AI_TRANSCRIPTION_ENABLED=true`, transcription model and size/text bounds, and the configured OpenAI key
- Google OAuth: `GOOGLE_OAUTH_ENABLED=true`, client ID/secret, and production success/failure URLs
- Resend invitation email: `EMAIL_ENABLED=true`, `EMAIL_PROVIDER=RESEND`, `RESEND_API_KEY`, and `RESEND_FROM`
- SMTP alternative: `EMAIL_PROVIDER=SMTP` with sender, timezone, host, port, credentials, and TLS settings
- Operational DLT API: `ASSETSPHERE_DLT_OPS_ENABLED=true`, exposed only to authorized operators

Disabled optional integrations must not require credentials. Never expose backend keys, provider secrets, SMTP credentials, JWT secrets, database credentials, or webhook secrets through `VITE_*` variables.

## Stripe TEST/SANDBOX Webhook

The deployed hackathon app intentionally uses Stripe TEST/SANDBOX credentials. Configure Stripe test events for the backend Stripe webhook endpoint documented in Swagger and store its signing secret as `STRIPE_WEBHOOK_SECRET`. Only signature-verified provider events activate or synchronize subscriptions; a browser success redirect is non-authoritative. Use live Stripe credentials only for a real production launch.

## Storage, Email, and Proxies

Production uses private S3-compatible storage; MinIO is the local implementation. Provision the production bucket before startup and keep bucket auto-creation disabled. Production invitation delivery uses Resend's HTTPS API; SMTP is an alternative transport and manual invitation-copy remains available when delivery is disabled or fails. Terminate HTTPS at a trusted proxy/load balancer and forward standard forwarding headers.

## Health Checks

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Aggregate health: `GET /actuator/health`

Use liveness only to restart a stuck process and readiness to control traffic. Restrict detailed actuator information to authorized operators.

## Deployment Order

1. Provision PostgreSQL/pgvector, Redis, Kafka/Redpanda, private object storage, DNS, and TLS.
2. Store required and feature-gated secrets in the hosting platform.
3. Apply Flyway migrations by starting one controlled backend deployment.
4. Verify readiness, object storage, Resend delivery, Stripe test webhook verification, and asynchronous processing.
5. Deploy the frontend with only the public HTTPS backend origin in `VITE_API_BASE_URL`.
