# AssetSphere Backend

Spring Boot 3 / Java 21 backend for the AssetSphere Spring Modulith modular monolith.

## Run locally

Prerequisites: Java 21, Maven 3.9+, and Docker Desktop. Set `POSTGRES_USER` and `POSTGRES_PASSWORD` before starting Docker. The local Compose database is published on port `5433` to avoid conflicts with a local PostgreSQL service; for a direct IntelliJ run, set `DB_USERNAME` and `DB_PASSWORD` to those same values.

Start local dependencies:

```bash
docker compose up -d postgres redis kafka kafka-ui minio
```

Run the application with the development profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The API starts at `http://localhost:8080`. Swagger UI is at `http://localhost:8080/swagger-ui.html`, health is at
`http://localhost:8080/actuator/health`, Kafka UI is at `http://localhost:8081`, and the MinIO console is at
`http://localhost:9001` (`minioadmin` / `minioadmin`).

## Payment modes

AssetSphere selects exactly one payment adapter through `ASSETSPHERE_PAYMENT_MODE`; credentials never select a
provider implicitly.

- Development Stripe: `ASSETSPHERE_PAYMENT_MODE=STRIPE` with Stripe test credentials. Hosted checkout and verified
  Stripe webhooks retain the existing behavior.
- Development local Razorpay: `ASSETSPHERE_PAYMENT_MODE=RAZORPAY_LOCAL`,
  `ASSETSPHERE_LOCAL_RAZORPAY_ENABLED=true`, `LOCAL_RAZORPAY_BASE_URL=http://localhost:8082`, and the merchant
  `LOCAL_RAZORPAY_KEY_ID`, `LOCAL_RAZORPAY_KEY_SECRET`, and `LOCAL_RAZORPAY_WEBHOOK_SECRET` values.
- Production: only `ASSETSPHERE_PAYMENT_MODE=STRIPE` is permitted. Selecting local Razorpay with the `prod` profile
  stops startup.

Both adapters implement AssetSphere's `PaymentGateway` boundary. Local Razorpay remains an independently runnable
HTTP service. Creating an order never activates PRO; only a verified webhook or the explicitly enabled DEV-only MY
poll-confirmation path can apply a successful payment.

To run the app in Docker as well, use `docker compose --profile app up --build`.

## Manual configuration

Set `ASSETSPHERE_JWT_SECRET` before running authentication-enabled environments. The development profile requires `DB_USERNAME` and
`DB_PASSWORD`; Docker derives them from `POSTGRES_USER` and `POSTGRES_PASSWORD`. Production additionally requires
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, Redis, Kafka, and S3-compatible storage configuration. The production image
defaults to `prod`; selected Stripe configuration is validated at startup.
