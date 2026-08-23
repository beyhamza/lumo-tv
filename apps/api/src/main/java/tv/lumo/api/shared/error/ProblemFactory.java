package tv.lumo.api.shared.error;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.ErrorCode;
import tv.lumo.api.generated.model.FieldError;
import tv.lumo.api.generated.model.Problem;

/**
 * Builds the RFC 7807 bodies this API returns.
 *
 * <p>{@code type} is derived mechanically from the error code
 * ({@code SOURCE_AUTH_FAILED} → {@code https://lumo.tv/errors/source-auth-failed}),
 * so a new code cannot ship with a missing or mistyped type URI.
 *
 * <p>{@code title} is English and non-localised by design: clients translate from
 * {@code code}, never from {@code title}.
 */
@Component
public class ProblemFactory {

    private static final String TYPE_BASE = "https://lumo.tv/errors/";

    public Problem create(int status, ErrorCode code, String detail, String instance, List<FieldError> fieldErrors) {
        Problem problem = new Problem(typeFor(code), titleFor(code), status, code);
        problem.setDetail(detail);
        problem.setInstance(instance);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            problem.setErrors(fieldErrors);
        }
        return problem;
    }

    public static URI typeFor(ErrorCode code) {
        return URI.create(TYPE_BASE + code.getValue().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** {@code SOURCE_AUTH_FAILED} → {@code "Source auth failed"}. */
    public static String titleFor(ErrorCode code) {
        String words = code.getValue().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
