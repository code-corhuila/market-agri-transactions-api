package co.edu.corhuila.marketagri.transactions.adapter.in.http;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Who calls, taken from a verified token (authentication.md §2). {@code subject} is the only
 * source of the caller's identity; {@code name} is absent in service tokens.
 */
public record Caller(String subject, String role, String name) {

    /** Request attribute where {@link AuthInterceptor} leaves the caller for the controllers. */
    public static final String ATTRIBUTE = "transactions.caller";

    static final String SERVICE_ROLE = "SERVICE";

    public boolean isService() {
        return SERVICE_ROLE.equals(role);
    }

    /**
     * The owner of an {@code Idempotency-Key} as the {@code uuid} column of B2 keeps it. A service
     * {@code sub} is {@code service:<name>} (ADR-020), not a UUID, so it becomes the name-based UUID
     * of the subject: always the same one for the same caller.
     */
    public UUID ownerId() {
        return UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8));
    }

    /** A user token has a UUID {@code sub} and a {@code name} (authentication.md §2); otherwise it is not valid. */
    public UUID userId() {
        if (name == null || name.isBlank()) {
            throw new UnauthorizedException("user token without name");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException notAUuid) {
            throw new UnauthorizedException("user token whose sub is not a UUID");
        }
    }
}
