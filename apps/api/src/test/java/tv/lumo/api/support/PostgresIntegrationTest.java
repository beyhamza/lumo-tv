package tv.lumo.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the real application context and a real database.
 *
 * <p>PostgreSQL 16 in a container, not H2. The schema uses {@code citext},
 * {@code pg_trgm} GIN indexes, partial unique indexes and {@code ON CONFLICT ...
 * WHERE}, none of which an in-memory substitute reproduces. A test suite that
 * passes on H2 and fails on the real database is worse than no test suite
 * (ADR 0002, ADR 0006).
 *
 * <p>The container is started once for the whole run by
 * {@link PostgresContainerInitializer} and reused across every test class, so
 * the ~3 s start-up is paid once rather than per class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class PostgresIntegrationTest {
}
