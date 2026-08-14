package com.assetsphere.modules.billing.persistence;

import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;

public final class BillingPostgresIntegrationDatabase {
    private static final Object MIGRATION_LOCK = new Object();

    private BillingPostgresIntegrationDatabase() {
    }

    public static PGSimpleDataSource migratedDataSource() {
        PGSimpleDataSource dataSource = dataSource();
        migrate(dataSource, "public", null);
        return dataSource;
    }

    public static void migrate(PGSimpleDataSource dataSource, String schema, String target) {
        synchronized (MIGRATION_LOCK) {
            var configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration");
            if (target != null) configuration.target(target);
            configuration.load().migrate();
        }
    }

    public static PGSimpleDataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl("jdbc:postgresql://%s:5432/assetsphere".formatted(required("ASSETSPHERE_POSTGRES_HOST")));
        dataSource.setUser("assetsphere");
        dataSource.setPassword(required("POSTGRES_PASSWORD"));
        return dataSource;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
