package com.neram.pos.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("Starting Flyway migration strategy with self-healing repair...");
            try {
                flyway.repair();
                log.info("Flyway repair executed successfully.");
            } catch (Exception e) {
                log.warn("Flyway repair non-fatal warning: {}", e.getMessage());
            }
            try {
                flyway.migrate();
                log.info("Flyway migrations executed successfully.");
            } catch (Exception e) {
                log.error("Flyway migrate encountered exception: {}. Continuing with defensive DatabaseSchemaEnsurer.", e.getMessage());
            }
        };
    }
}
