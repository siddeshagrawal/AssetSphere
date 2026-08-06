# AssetSphere Backend

Spring Boot 3 / Java 21 bootstrap for the AssetSphere Spring Modulith modular monolith. This phase intentionally
contains no business-module implementation, authentication flow, upload flow, search flow, or AI workflow.

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

To run the app in Docker as well, use `docker compose --profile app up --build`.

## Manual configuration

Set `ASSETSPHERE_JWT_SECRET` before implementing authentication. The development profile requires `DB_USERNAME` and
`DB_PASSWORD`; Docker derives them from `POSTGRES_USER` and `POSTGRES_PASSWORD`. Production additionally requires
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, and `KAFKA_BOOTSTRAP_SERVERS`. Configure the production object-storage
provider when the Storage module is implemented; the current MinIO adapter is enabled only for the development profile.
