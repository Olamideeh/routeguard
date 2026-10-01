package com.example.routeguard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5439/routeguard_migration_test_db",
        "spring.datasource.username=routeguard_test_user",
        "spring.datasource.password=routeguard_test_password",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.locations=classpath:db/migration"
})
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void initialMigrationCreatesSchemaAndRecordsVersion() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name IN (
                      'company_api_credentials',
                      'delivery_companies',
                      'delivery_evaluations',
                      'delivery_events',
                      'evaluation_reason_codes',
                      'platform_users',
                      'risk_reviews'
                  )
                """, Integer.class);

        Integer migrationCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '1'
                  AND type = 'SQL'
                  AND success = true
                """, Integer.class);

        assertEquals(7, tableCount);
        assertEquals(1, migrationCount);
    }
}