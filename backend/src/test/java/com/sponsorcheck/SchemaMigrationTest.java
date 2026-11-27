package com.sponsorcheck;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts the whole application against a real Postgres 17 (D17)
 * and checks that the Flyway migration created what the matcher needs.
 */
@SpringBootTest
@Testcontainers
class SchemaMigrationTest {

    @Container
    @ServiceConnection // points spring.datasource.* at this container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    JdbcClient jdbc;

    @Test
    void trigramExtensionIsInstalled() {
        long count = jdbc.sql("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'")
                .query(Long.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void trigramSimilarityWorksOnNormalisedNames() {
        jdbc.sql("""
                INSERT INTO register_entry (country, legal_name, normalised_name, data_as_of)
                VALUES ('GB', 'Monzo Bank Limited', 'monzo bank', DATE '2026-11-19')
                """).update();

        double score = jdbc.sql("SELECT similarity(normalised_name, 'monzo') FROM register_entry")
                .query(Double.class).single();

        assertThat(score).isGreaterThan(0.3);
    }
}
