package training.QGChat.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import training.QGChat.auth.dto.AuthResponse;
import training.QGChat.auth.dto.ForgotPasswordRequest;
import training.QGChat.auth.dto.ForgotPasswordResponse;
import training.QGChat.auth.dto.LoginRequest;
import training.QGChat.auth.dto.LogoutResponse;
import training.QGChat.auth.dto.RegisterRequest;
import training.QGChat.auth.dto.ResetPasswordRequest;
import training.QGChat.auth.dto.ResetPasswordResponse;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.auth.model.UserAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final String BEARER_PREFIX = "Bearer ";
    // 寫入 email_delivery_logs 的模板代碼，方便後續背景寄信服務撈取。
    private static final String PASSWORD_RESET_TEMPLATE = "PASSWORD_RESET";
    private static final String PASSWORD_RESET_SUBJECT = "Reset your QGChat password";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long tokenTtlHours;
    private final long passwordResetTtlMinutes;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${qgchat.auth.token-ttl-hours:24}") long tokenTtlHours,
            @Value("${qgchat.auth.password-reset-ttl-minutes:30}") long passwordResetTtlMinutes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtlHours = tokenTtlHours;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        // username 與 email 都需要唯一，先用不分大小寫方式檢查，避免建立重複帳號。
        if (existsByUsername(request.username())) {
            throw new AuthException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (existsByEmail(request.email())) {
            throw new AuthException(HttpStatus.CONFLICT, "Email already exists");
        }

        UserAccount user = jdbcTemplate.queryForObject("""
                        INSERT INTO users (account_type, username, email, password_hash, display_name)
                        VALUES ('MEMBER', ?, ?, ?, ?)
                        RETURNING id, username, email, display_name, password_hash, status
                        """,
                UserAccount.rowMapper(),
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName());

        // 註冊成功後直接建立 session，讓前端可以無縫進入登入狀態。
        return createSession(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        // account 可接受 username 或 email，實際驗證時再比對 BCrypt 密碼。
        UserAccount user = findByUsernameOrEmail(request.account())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid account or password"));

        if (!"ACTIVE".equals(user.status()) || user.passwordHash() == null
                || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid account or password");
        }

        jdbcTemplate.update("UPDATE users SET last_seen_at = CURRENT_TIMESTAMP WHERE id = ?", user.id());
        // 每次登入都建立新的 bearer token，支援多裝置登入。
        return createSession(user, ipAddress, userAgent);
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request, String ipAddress, String userAgent) {
        Optional<UserAccount> user = findByEmail(request.email());
        if (user.isEmpty() || !"ACTIVE".equals(user.get().status())) {
            // 不透露 email 是否存在，降低帳號列舉風險。
            return ForgotPasswordResponse.accepted(null);
        }

        String resetToken = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(passwordResetTtlMinutes);

        jdbcTemplate.update("""
                        INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, requested_ip, user_agent)
                        VALUES (?, ?, ?, CAST(NULLIF(?, '') AS inet), ?)
                        """,
                user.get().id(), hash(resetToken), expiresAt, ipAddress, userAgent);

        jdbcTemplate.update("""
                        INSERT INTO email_delivery_logs (user_id, email, template_key, subject, status)
                        VALUES (?, ?, ?, ?, 'PENDING')
                        """,
                user.get().id(), user.get().email(), PASSWORD_RESET_TEMPLATE, PASSWORD_RESET_SUBJECT);

        // 目前直接回傳 resetToken，方便本機/測試環境使用；正式寄信可由 email log 消費。
        return ForgotPasswordResponse.accepted(resetToken);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        // consumePasswordResetToken 會同時標記 used_at，避免同一 token 被重複使用。
        UUID userId = consumePasswordResetToken(request.token())
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"));

        int updatedUsers = jdbcTemplate.update("""
                        UPDATE users
                        SET password_hash = ?
                        WHERE id = ?
                          AND account_type = 'MEMBER'
                          AND status = 'ACTIVE'
                        """,
                passwordEncoder.encode(request.newPassword()), userId);

        if (updatedUsers == 0) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
        }

        // 密碼重設後撤銷該使用者所有既有 session，要求重新登入。
        jdbcTemplate.update("""
                UPDATE guest_sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND revoked_at IS NULL
                """, userId);

        return new ResetPasswordResponse(true);
    }

    @Transactional
    public LogoutResponse logout(String authorization) {
        String token = parseBearerToken(authorization)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Missing bearer token"));

        // 登出採軟撤銷 session，保留歷史紀錄但讓 token 立即失效。
        int revoked = jdbcTemplate.update("""
                UPDATE guest_sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE session_token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """, hash(token));

        return new LogoutResponse(revoked > 0);
    }

    private AuthResponse createSession(UserAccount user, String ipAddress, String userAgent) {
        String token = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(tokenTtlHours);

        // 資料庫只保存 token hash；明文 token 只在建立當下回傳給 client。
        jdbcTemplate.update("""
                        INSERT INTO guest_sessions (user_id, session_token_hash, ip_address, user_agent, expires_at)
                        VALUES (?, ?, CAST(NULLIF(?, '') AS inet), ?, ?)
                        """,
                user.id(), hash(token), ipAddress, userAgent, expiresAt);

        return AuthResponse.bearer(token, expiresAt,
                new AuthResponse.UserResponse(user.id(), user.username(), user.email(), user.displayName()));
    }

    private Optional<UUID> consumePasswordResetToken(String token) {
        try {
            // 用 UPDATE ... RETURNING 原子化消費 token，避免並發請求重複重設密碼。
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            UPDATE password_reset_tokens
                            SET used_at = CURRENT_TIMESTAMP
                            WHERE token_hash = ?
                              AND used_at IS NULL
                              AND expires_at > CURRENT_TIMESTAMP
                            RETURNING user_id
                            """,
                    UUID.class,
                    hash(token)));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private boolean existsByUsername(String username) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(username) = LOWER(?))",
                Boolean.class,
                username);
        return Boolean.TRUE.equals(exists);
    }

    private boolean existsByEmail(String email) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE LOWER(email) = LOWER(?))",
                Boolean.class,
                email);
        return Boolean.TRUE.equals(exists);
    }

    private Optional<UserAccount> findByUsernameOrEmail(String account) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT id, username, email, display_name, password_hash, status
                            FROM users
                            WHERE account_type = 'MEMBER'
                              AND (LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?))
                            """,
                    UserAccount.rowMapper(),
                    account,
                    account));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<UserAccount> findByEmail(String email) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT id, username, email, display_name, password_hash, status
                            FROM users
                            WHERE account_type = 'MEMBER' AND LOWER(email) = LOWER(?)
                            """,
                    UserAccount.rowMapper(),
                    email));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> parseBearerToken(String authorization) {
        // 僅接受標準 Authorization: Bearer <token> 格式。
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private String randomToken() {
        // 32 bytes 隨機值再以 URL-safe Base64 表示，適合作為 bearer/reset token。
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            // session/reset token 統一用 SHA-256 hash 後存放。
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
