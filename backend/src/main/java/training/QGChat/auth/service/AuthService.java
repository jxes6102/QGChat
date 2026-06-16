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
import training.QGChat.auth.exception.ErrorCode;
import training.QGChat.auth.model.UserAccount;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    private static final String PASSWORD_RESET_TEMPLATE = "PASSWORD_RESET";
    private static final String PASSWORD_RESET_SUBJECT = "Reset your QGChat password";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuthService sessionAuthService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long tokenTtlHours;
    private final long passwordResetTtlMinutes;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            SessionAuthService sessionAuthService,
            @Value("${qgchat.auth.token-ttl-hours:24}") long tokenTtlHours,
            @Value("${qgchat.auth.password-reset-ttl-minutes:30}") long passwordResetTtlMinutes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.sessionAuthService = sessionAuthService;
        this.tokenTtlHours = tokenTtlHours;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (existsByUsername(request.username())) {
            throw new AuthException(HttpStatus.CONFLICT, ErrorCode.USERNAME_ALREADY_EXISTS, "使用者名稱已被使用");
        }
        if (existsByEmail(request.email())) {
            throw new AuthException(HttpStatus.CONFLICT, ErrorCode.EMAIL_ALREADY_EXISTS, "電子郵件已被使用");
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

        return createSession(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        UserAccount user = findByUsernameOrEmail(request.account())
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_ACCOUNT_OR_PASSWORD, "帳號或密碼錯誤"));

        if (!"ACTIVE".equals(user.status()) || user.passwordHash() == null
                || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_ACCOUNT_OR_PASSWORD, "帳號或密碼錯誤");
        }

        jdbcTemplate.update("UPDATE users SET last_seen_at = CURRENT_TIMESTAMP WHERE id = ?", user.id());
        return createSession(user, ipAddress, userAgent);
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request, String ipAddress, String userAgent) {
        Optional<UserAccount> user = findByEmail(request.email());
        if (user.isEmpty() || !"ACTIVE".equals(user.get().status())) {
            return ForgotPasswordResponse.accepted(null);
        }

        String resetToken = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(passwordResetTtlMinutes);

        jdbcTemplate.update("""
                        INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, requested_ip, user_agent)
                        VALUES (?, ?, ?, CAST(NULLIF(?, '') AS inet), ?)
                        """,
                user.get().id(), sessionAuthService.hashToken(resetToken), expiresAt, ipAddress, userAgent);

        jdbcTemplate.update("""
                        INSERT INTO email_delivery_logs (user_id, email, template_key, subject, status)
                        VALUES (?, ?, ?, ?, 'PENDING')
                        """,
                user.get().id(), user.get().email(), PASSWORD_RESET_TEMPLATE, PASSWORD_RESET_SUBJECT);

        return ForgotPasswordResponse.accepted(resetToken);
    }

    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        UUID userId = consumePasswordResetToken(request.token())
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESET_TOKEN, "重設密碼連結無效或已過期"));

        int updatedUsers = jdbcTemplate.update("""
                        UPDATE users
                        SET password_hash = ?
                        WHERE id = ?
                          AND account_type = 'MEMBER'
                          AND status = 'ACTIVE'
                        """,
                passwordEncoder.encode(request.newPassword()), userId);

        if (updatedUsers == 0) {
            throw new AuthException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESET_TOKEN, "重設密碼連結無效或已過期");
        }

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
        String tokenHash = sessionAuthService.currentTokenHash(authorization);

        int revoked = jdbcTemplate.update("""
                UPDATE guest_sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE session_token_hash = ?
                  AND revoked_at IS NULL
                  AND expires_at > CURRENT_TIMESTAMP
                """, tokenHash);

        return new LogoutResponse(revoked > 0);
    }

    private AuthResponse createSession(UserAccount user, String ipAddress, String userAgent) {
        String token = randomToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(tokenTtlHours);

        jdbcTemplate.update("""
                        INSERT INTO guest_sessions (user_id, session_token_hash, ip_address, user_agent, expires_at)
                        VALUES (?, ?, CAST(NULLIF(?, '') AS inet), ?, ?)
                        """,
                user.id(), sessionAuthService.hashToken(token), ipAddress, userAgent, expiresAt);

        return AuthResponse.bearer(token, expiresAt,
                new AuthResponse.UserResponse(user.id(), user.username(), user.email(), user.displayName()));
    }

    private Optional<UUID> consumePasswordResetToken(String token) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            UPDATE password_reset_tokens
                            SET used_at = CURRENT_TIMESTAMP
                            WHERE token_hash = ?
                              AND used_at IS NULL
                              AND expires_at > CURRENT_TIMESTAMP
                            RETURNING user_id
                            """,
                    UUID.class,
                    sessionAuthService.hashToken(token)));
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

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
