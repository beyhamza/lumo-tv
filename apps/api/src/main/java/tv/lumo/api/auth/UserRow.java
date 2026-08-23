package tv.lumo.api.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A row of {@code "user"}.
 *
 * <p>{@code passwordHash} is null for SSO-only accounts. It is in this record
 * because sign-in needs it, and nowhere near any response mapping: the API model
 * {@code tv.lumo.api.generated.model.User} has no such property.
 */
public record UserRow(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        String locale,
        OffsetDateTime emailVerifiedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
