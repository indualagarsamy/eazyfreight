package com.eazyfreight;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the application against a Flyway-migrated schema with
 * {@code ddl-auto: validate}, so a mismatch between a migration and an entity
 * mapping fails the build rather than the first production start-up.
 *
 * <p>The reference project this codebase follows had a V1 migration describing an
 * entirely different table from the one its entities mapped to. That is the failure
 * this test exists to prevent.
 */
@SpringBootTest
@ActiveProfiles("migrations")
class MigrationSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsProduceASchemaTheEntityMappingsValidateAgainst() {
        // Reaching here at all means Hibernate's schema validation passed.
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "flyway_schema_history",
                "reference_sequences",
                "lanes",
                "rates",
                "surcharges",
                "quotes",
                "quote_lines",
                "quote_cargo_details",
                "carrier_bookings",
                "truck_delivery_orders",
                "bookings",
                "booking_cargo_details",
                "booking_status_history",
                "booking_reinstatements",
                "eei_filings",
                "itn_records",
                "eei_filing_history",
                "export_licenses");
    }

    @Test
    void allPortableMigrationsApplied() {
        // Flyway records a null-version row for creating the schema itself; skip it.
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                String.class);

        assertThat(versions).containsExactly("1", "2", "3", "4");
    }
}
