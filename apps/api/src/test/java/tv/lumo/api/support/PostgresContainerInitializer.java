package tv.lumo.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Starts one PostgreSQL 16 container for the whole test run.
 *
 * <p>Deliberately a static singleton rather than a JUnit-managed
 * {@code @Container}: the latter is torn down per class, and paying container
 * start-up once per test class dominates the run time of the whole suite.
 * Liquibase runs against it on the first context refresh.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerInitializer {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("lumo")
                    .withUsername("lumo")
                    .withPassword("lumo");

    static {
        POSTGRES.start();
    }

    /**
     * Points the datasource at the container.
     *
     * <p>A registrar rather than {@code @DynamicPropertySource} so this works from
     * an imported configuration instead of having to be repeated as a static
     * method on every test class.
     */
    @Bean
    DynamicPropertyRegistrar postgresProperties() {
        return registry -> {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        };
    }
}
