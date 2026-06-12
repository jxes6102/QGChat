package training.QGChat.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// 登入或註冊成功後回傳的 token 與使用者基本資料。
public record AuthResponse(
        String tokenType,
        String accessToken,
        OffsetDateTime expiresAt,
        UserResponse user
        ) {

    public static AuthResponse bearer(String accessToken, OffsetDateTime expiresAt, UserResponse user) {
        // 目前只支援 Bearer token，集中在這裡建立可避免 controller 重複寫字串。
        return new AuthResponse("Bearer", accessToken, expiresAt, user);
    }

    // token 回應中附帶的簡化使用者資料。
    public record UserResponse(
            UUID id,
            String username,
            String email,
            String displayName
            ) {

    }
}
