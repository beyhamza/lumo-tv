package tv.lumo.api.auth;

import org.springframework.stereotype.Component;
import tv.lumo.api.generated.model.Locale;
import tv.lumo.api.generated.model.User;

/**
 * {@link UserRow} to the contract's {@code User}.
 *
 * <p>The mapping is deliberately narrow: {@code password_hash} exists on the row
 * and on no response. The generated {@code User} has no such property, so this
 * is enforced by the type system rather than by remembering.
 */
@Component
public class UserMapper {

    public User toApi(UserRow row) {
        User user = new User(row.id(), row.email(), Locale.fromValue(row.locale()),
                row.createdAt(), row.updatedAt());
        user.setDisplayName(row.displayName());
        user.setEmailVerifiedAt(row.emailVerifiedAt());
        return user;
    }
}
