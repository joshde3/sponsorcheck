package com.sponsorcheck;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One throwaway Postgres 17 shared by all database tests (DECISIONS.md D17). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection // points spring.datasource.* at this container
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
