# Development Test-Data Cleanup

Do not expose a reset endpoint. Prefer resetting the entire disposable development PostgreSQL schema, Redis database,
Kafka topics, and object-storage bucket together.

For selective cleanup, first verify the target user owns no workspace shared with another active member. Remove only
exclusively owned workspace graphs in foreign-key dependency order, then memberships, OAuth/login/session records, and
finally the user. Never delete a shared workspace to remove one test user. Take a database backup and inspect counts in
a transaction before committing; the exact SQL must be adapted to the deployed migration version.
