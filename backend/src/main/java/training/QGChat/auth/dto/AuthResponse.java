package training.QGChat.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        OffsetDateTime expiresAt,
        UserResponse user
        ) {

    public static AuthResponse bearer(String accessToken, OffsetDateTime expiresAt, UserResponse user) {
        return new AuthResponse("Bearer", accessToken, expiresAt, user);
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            String displayName
            ) {

    }
}
