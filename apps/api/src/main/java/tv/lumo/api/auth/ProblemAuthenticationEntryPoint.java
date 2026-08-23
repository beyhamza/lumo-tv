package tv.lumo.api.auth;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.shared.error.ProblemFactory;

/**
 * Answers authentication and authorisation failures in
 * {@code application/problem+json}.
 *
 * <p>Spring Security's filters run before the dispatcher servlet, so
 * {@code GlobalExceptionHandler} never sees them: without this, a missing token
 * would produce an empty 401 with a {@code WWW-Authenticate} header and no
 * {@code code} for a client to branch on.
 *
 * <p>The ObjectMapper import is {@code tools.jackson.databind}, not
 * {@code com.fasterxml.jackson.databind}: Spring Boot 4 ships Jackson 3, which
 * moved core and databind to the {@code tools.jackson} namespace. Annotations
 * did NOT move and remain {@code com.fasterxml.jackson.annotation}, which is why
 * the generated models still compile (AGENTS.md §4).
 *
 * <p>The distinction that matters to clients is
 * {@code ACCESS_TOKEN_EXPIRED} versus {@code UNAUTHENTICATED}: the first means
 * "refresh once and replay", the second means "sign in again". Getting it wrong
 * either signs users out needlessly or sends them into a refresh loop.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemFactory problems;
    private final ObjectMapper objectMapper;

    public ProblemAuthenticationEntryPoint(ProblemFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = isExpired(authException) ? ErrorCode.ACCESS_TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED;
        write(request, response, HttpStatus.UNAUTHORIZED, code, "Authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied");
    }

    /**
     * Spring Security reports expiry inside the description of an
     * {@link InvalidBearerTokenException} rather than as a distinct type, so this
     * has to read the message. Kept narrow and defensive: an unrecognised message
     * falls back to {@code UNAUTHENTICATED}, which is the safe direction — a
     * client told to sign in again always recovers, a client told to refresh a
     * dead token loops.
     */
    private static boolean isExpired(AuthenticationException exception) {
        if (!(exception instanceof InvalidBearerTokenException)) {
            return false;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("expired");
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, ErrorCode code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                problems.create(status.value(), code, detail, request.getRequestURI(), List.of()));
    }
}
