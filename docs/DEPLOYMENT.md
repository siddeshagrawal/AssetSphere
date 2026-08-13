# AssetSphere deployment

AssetSphere uses explicit Spring profiles. Local development uses `dev`; hosted deployments must set `SPRING_PROFILES_ACTIVE=prod`. Keep credentials in the deployment platform's secret store, never in source, frontend variables, images, or logs.

## Environment matrix

| Dependency / feature | Development | Production |
| --- | --- | --- |
| Frontend | `http://localhost:5173` | Public HTTPS origin |
| Backend | `http://localhost:8080` | Public HTTPS origin behind a trusted proxy/load balancer |
| PostgreSQL + pgvector | `localhost:5433` | External managed PostgreSQL with pgvector |
| Redis | `localhost:6379` | Hosted Redis with authentication and TLS (`rediss://` or SSL properties) |
| Kafka | `localhost:29092` | Hosted Kafka with SASL/SSL configuration |
| Object storage | MinIO at `http://localhost:9000` | S3-compatible HTTPS endpoint; bucket provisioned outside the app |
| OpenAI | Optional and feature-gated | Production API key and explicitly enabled capabilities |
| Google OAuth | Optional localhost redirect configuration | Production client and HTTPS redirect URLs |
| Email | Optional SMTP; manual invitation-copy remains available | Production SMTP and verified sender |
| Payments | Stripe test mode or `RAZORPAY_LOCAL` | Stripe only |

## Required production configuration

- `SPRING_PROFILES_ACTIVE=prod`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_URL` (or `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, and `REDIS_SSL_ENABLED`)
- `KAFKA_BOOTSTRAP_SERVERS`; for hosted clusters also configure `KAFKA_SECURITY_PROTOCOL`, `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG`, and `KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM`
- `ASSETSPHERE_JWT_SECRET`
- `ASSETSPHERE_CORS_ALLOWED_ORIGINS` and `FRONTEND_BASE_URL`, both using production HTTPS origins
- `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, and `MINIO_BUCKET`; leave bucket auto-creation disabled in production
- `ASSETSPHERE_PAYMENT_MODE=STRIPE`, `STRIPE_ENABLED=true`, `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET`, and `STRIPE_PRO_PRICE_ID`; checkout return URLs are derived from `FRONTEND_BASE_URL` per workspace

Production configuration must not contain production-critical localhost URLs. `RAZORPAY_LOCAL` and local payment polling/card-demo features are rejected in the production profile. Stripe mode validates required Stripe configuration at startup.

## Optional feature-gated configuration

- Generative AI: `ASSETSPHERE_AI_ENABLED=true`, `OPENAI_API_KEY`, chat model configuration
- Semantic embeddings: `ASSETSPHERE_AI_EMBEDDING_ENABLED=true`, `ASSETSPHERE_AI_SPRING_EMBEDDING_MODEL=openai`, `ASSETSPHERE_AI_EMBEDDING_MODEL=text-embedding-3-small`, dimension `1536`
- Video transcription: `ASSETSPHERE_AI_TRANSCRIPTION_ENABLED=true`, `ASSETSPHERE_AI_TRANSCRIPTION_MODEL`, size/text bounds, and the configured OpenAI key
- Google OAuth: `GOOGLE_OAUTH_ENABLED=true`, client ID/secret, and production success/failure URLs
- Invitation email: `EMAIL_ENABLED=true`, sender, timezone, and SMTP host/port/credentials/TLS settings
- Operational DLT API: `ASSETSPHERE_DLT_OPS_ENABLED=true`; expose only to authorized operators

Disabled optional integrations must not require credentials. Do not expose any backend key, provider secret, SMTP credential, JWT secret, database credential, or webhook secret through `VITE_*` variables.

## Stripe webhook

Configure Stripe to send events to the deployed AssetSphere Stripe webhook endpoint documented by the backend OpenAPI contract. Store the signing secret as `STRIPE_WEBHOOK_SECRET`. Only signature-verified provider events activate or update subscriptions; browser checkout completion is never authoritative. Use Stripe test keys/events in development and live keys/events only in production.

## Storage and proxies

Provision the production bucket before startup; `MINIO_AUTO_CREATE_BUCKET` must remain `false`. Terminate HTTPS at a trusted proxy/load balancer and forward standard forwarding headers. The production profile uses Spring's framework forwarded-header strategy.

## Health checks

- Liveness: `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Aggregate health: `GET /actuator/health`

Use liveness only to restart a stuck process and readiness to control traffic. Restrict detailed actuator information to authorized operators.

## Deployment order

1. Provision PostgreSQL/pgvector, Redis, Kafka, object storage, DNS, and TLS.
2. Store secrets and required environment configuration in the hosting platform.
3. Apply Flyway migrations by starting one controlled backend deployment.
4. Verify readiness, Stripe webhook delivery/signature validation, storage access, and asynchronous processing.
5. Deploy the frontend with only the public HTTPS backend origin in `VITE_API_BASE_URL`.
