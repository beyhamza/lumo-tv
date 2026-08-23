package tv.lumo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of {@code lumo-api}.
 *
 * <p>Virtual threads are enabled in {@code application.yml}
 * ({@code spring.threads.virtual.enabled}). The whole codebase is written in
 * blocking, sequential style on purpose: no WebFlux, no {@code Mono}/{@code Flux},
 * no chained {@code CompletableFuture} in business logic (ADR 0005). Stack traces
 * stay readable and the debugger stays useful.
 *
 * <p>The corollary is that backpressure is explicit rather than inherited from a
 * bounded thread pool — see {@code tv.lumo.api.ingest.HostConcurrencyLimiter}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LumoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumoApiApplication.class, args);
    }
}
