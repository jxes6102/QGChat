package training.QGChat.profile.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.profile.dto.ChangeEmailRequest;
import training.QGChat.profile.dto.ChangePasswordRequest;
import training.QGChat.profile.dto.UpdateAvatarRequest;
import training.QGChat.profile.dto.UpdateDisplayNameRequest;
import training.QGChat.profile.dto.UpdateProfileRequest;
import training.QGChat.profile.dto.UserProfileResponse;
import training.QGChat.profile.model.UserProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProfileService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfileResponse getProfile(String authorization) {
        return getCurrentUser(authorization).toResponse();
    }

    @Transactional
    public UserProfileResponse updateProfile(String authorization, UpdateProfileRequest request) {
        UserProfile currentUser = getCurrentUser(authorization);
        String displayName = normalizeRequiredText(request.displayName(), "Display name cannot be blank");
        String email = normalizeEmail(request.email());
        String avatarUrl = normalizeOptionalText(request.avatarUrl());

        if (displayName == null && email == null && request.avatarUrl() == null) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "No profile fields to update");
        }

        if (email != null && existsByEmailForOtherUser(email, currentUser.id())) {
            throw new AuthException(HttpStatus.CONFLICT, "Email already exists");
        }

        jdbcTemplate.update("""
                        UPDATE users
                        SET display_name = COALESCE(?, display_name),
                            email = COALESCE(?, email),
                            avatar_url = CASE WHEN ? THEN ? ELSE avatar_url END
                        WHERE id = ?
                        """,
                displayName,
                email,
                request.avatarUrl() != null,
                avatarUrl,
                currentUser.id());

        return findProfileById(currentUser.id()).toResponse();
    }

    @Transactional
    public UserProfileResponse updateDisplayName(String authorization, UpdateDisplayNameRequest request) {
        UserProfile currentUser = getCurrentUser(authorization);
        jdbcTemplate.update("""
                        UPDATE users
                        SET display_name = ?
                        WHERE id = ?
                        """,
                request.displayName().trim(),
                currentUser.id());

        return findProfileById(currentUser.id()).toResponse();
    }

    @Transactional
    public UserProfileResponse updateAvatar(String authorization, UpdateAvatarRequest request) {
        UserProfile currentUser = getCurrentUser(authorization);
        jdbcTemplate.update("""
                        UPDATE users
                        SET avatar_url = ?
                        WHERE id = ?
                        """,
                normalizeOptionalText(request.avatarUrl()),
                currentUser.id());

        return findProfileById(currentUser.id()).toResponse();
    }

    @Transactional
    public UserProfileResponse changeEmail(String authorization, ChangeEmailRequest request) {
        UserProfile currentUser = getCurrentUser(authorization);
        String email = request.email().trim();

        if (existsByEmailForOtherUser(email, currentUser.id())) {
            throw new AuthException(HttpStatus.CONFLICT, "Email already exists");
        }

        jdbcTemplate.update("""
                        UPDATE users
                        SET email = ?
                        WHERE id = ?
                        """,
                email,
                currentUser.id());

        return findProfileById(currentUser.id()).toResponse();
    }

    @Transactional
    public boolean changePassword(String authorization, ChangePasswordRequest request) {
        UserProfile currentUser = getCurrentUser(authorization);

        if (currentUser.passwordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), currentUser.passwordHash())) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid current password");
        }

        jdbcTemplate.update("""
                        UPDATE users
                        SET password_hash = ?
                        WHERE id = ?
                        """,
                passwordEncoder.encode(request.newPassword()),
                currentUser.id());

        jdbcTemplate.update("""
                UPDATE guest_sessions
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND session_token_hash <> ?
                  AND revoked_at IS NULL
                """, currentUser.id(), currentTokenHash(authorization));

        return true;
    }

    private UserProfile getCurrentUser(String authorization) {
        String tokenHash = currentTokenHash(authorization);
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT u.id, u.username, u.email, u.display_name, u.avatar_url, u.status,
                                   u.last_seen_at, u.created_at, u.updated_at, u.password_hash
                            FROM guest_sessions s
                            JOIN users u ON u.id = s.user_id
                            WHERE s.session_token_hash = ?
                              AND s.revoked_at IS NULL
                              AND s.expires_at > CURRENT_TIMESTAMP
                              AND u.account_type = 'MEMBER'
                              AND u.status = 'ACTIVE'
                            """,
                    UserProfile.rowMapper(),
                    tokenHash);
        } catch (EmptyResultDataAccessException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or expired bearer token");
        }
    }

    private UserProfile findProfileById(UUID userId) {
        return jdbcTemplate.queryForObject("""
                        SELECT id, username, email, display_name, avatar_url, status,
                               last_seen_at, created_at, updated_at, password_hash
                        FROM users
                        WHERE id = ?
                        """,
                UserProfile.rowMapper(),
                userId);
    }

    private boolean existsByEmailForOtherUser(String email, UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM users
                            WHERE LOWER(email) = LOWER(?)
                              AND id <> ?
                        )
                        """,
                Boolean.class,
                email,
                userId);
        return Boolean.TRUE.equals(exists);
    }

    private String currentTokenHash(String authorization) {
        String token = parseBearerToken(authorization)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Missing bearer token"));
        return hash(token);
    }

    private Optional<String> parseBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, blankMessage);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Email cannot be blank");
        }
        return normalized;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
