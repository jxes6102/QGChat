package training.QGChat.auth.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.auth.exception.ErrorCode;
import training.QGChat.auth.model.AuthenticatedSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionAuthService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JdbcTemplate jdbcTemplate;

    public SessionAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthenticatedSession requireActiveSession(String authorization) {
        String tokenHash = currentTokenHash(authorization);
        try {
            UUID userId = jdbcTemplate.queryForObject("""
                            SELECT u.id
                            FROM guest_sessions s
                            JOIN users u ON u.id = s.user_id
                            WHERE s.session_token_hash = ?
                              AND s.revoked_at IS NULL
                              AND s.expires_at > CURRENT_TIMESTAMP
                              AND u.status = 'ACTIVE'
                            """,
                    UUID.class,
                    tokenHash);
            return new AuthenticatedSession(userId, tokenHash);
        } catch (EmptyResultDataAccessException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, "登入狀態已失效，請重新登入");
        }
    }

    public AuthenticatedSession requireActiveMemberSession(String authorization) {
        String tokenHash = currentTokenHash(authorization);
        try {
            UUID userId = jdbcTemplate.queryForObject("""
                            SELECT u.id
                            FROM guest_sessions s
                            JOIN users u ON u.id = s.user_id
                            WHERE s.session_token_hash = ?
                              AND s.revoked_at IS NULL
                              AND s.expires_at > CURRENT_TIMESTAMP
                              AND u.account_type = 'MEMBER'
                              AND u.status = 'ACTIVE'
                            """,
                    UUID.class,
                    tokenHash);
            return new AuthenticatedSession(userId, tokenHash);
        } catch (EmptyResultDataAccessException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN, "登入狀態已失效，請重新登入");
        }
    }

    public String currentTokenHash(String authorization) {
        String token = parseBearerToken(authorization)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, ErrorCode.MISSING_TOKEN, "請先登入"));
        return hashToken(token);
    }

    public Optional<String> parseBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    public String hashToken(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
