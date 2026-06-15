package training.QGChat.auth.model;

import java.util.UUID;

public record AuthenticatedSession(
        UUID userId,
        String tokenHash
) {
}
