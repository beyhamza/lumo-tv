package tv.lumo.api.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Puts every controller under {@code /v1}.
 *
 * <p>The contract declares its servers as {@code https://api.lumo.tv/v1}, and
 * openapi-generator strips that base path from the generated paths — so the
 * generated interfaces map {@code /sources}, not {@code /v1/sources}.
 *
 * <p>This is a path prefix on our own package rather than
 * {@code server.servlet.context-path} on purpose: a context path would move the
 * actuator to {@code /v1/actuator/health} too, and the container health check in
 * docker-compose.yml probes {@code /actuator/health}. Versioning the API should
 * not move the liveness probe.
 */
@Configuration
public class ApiPathConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX,
                org.springframework.web.method.HandlerTypePredicate.forBasePackage("tv.lumo.api"));
    }
}
