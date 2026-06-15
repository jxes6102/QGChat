package training.QGChat.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.auth.service.SessionAuthService;
import training.QGChat.chat.dto.AddGroupMembersRequest;
import training.QGChat.chat.dto.ChatMessageResponse;
import training.QGChat.chat.dto.ConversationResponse;
import training.QGChat.chat.dto.CreateDirectConversationRequest;
import training.QGChat.chat.dto.CreateGroupConversationRequest;
import training.QGChat.chat.dto.MarkReadRequest;
import training.QGChat.chat.dto.SendMessageRequest;
import training.QGChat.chat.model.ChatMessage;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {
    // 與資料庫 schema 的 message_type enum 保持一致。
    private static final Set<String> MESSAGE_TYPES = Set.of("TEXT", "IMAGE", "FILE", "SYSTEM");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SessionAuthService sessionAuthService;

    public ChatService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, SessionAuthService sessionAuthService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sessionAuthService = sessionAuthService;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(String authorization) {
        UUID userId = requireUserId(authorization);
        // 查出目前使用者參與的所有對話，包含群組資訊、私聊對象、最後一則訊息與未讀數。
        return jdbcTemplate.query("""
                        SELECT c.id,
                               c.type::text AS type,
                               c.group_id,
                               g.name AS group_name,
                               g.avatar_url AS group_avatar_url,
                               du.id AS direct_user_id,
                               du.display_name AS direct_display_name,
                               du.avatar_url AS direct_avatar_url,
                               lm.id AS last_message_id,
                               lm.sender_id AS last_sender_id,
                               lms.display_name AS last_sender_display_name,
                               lm.type::text AS last_type,
                               lm.content AS last_content,
                               lm.metadata::text AS last_metadata,
                               lm.reply_to_message_id AS last_reply_to_message_id,
                               lm.sent_at AS last_sent_at,
                               lm.edited_at AS last_edited_at,
                               lm.deleted_at AS last_deleted_at,
                               COUNT(um.id) AS unread_count,
                               c.updated_at
                        FROM conversation_participants cp
                        JOIN conversations c ON c.id = cp.conversation_id
                        LEFT JOIN chat_groups g ON g.id = c.group_id
                        LEFT JOIN direct_conversations dc ON dc.conversation_id = c.id
                        LEFT JOIN users du ON du.id = CASE
                            -- 私聊時取出「另一位」使用者，供前端顯示對方名稱與頭像。
                            WHEN dc.user_one_id = ? THEN dc.user_two_id
                            ELSE dc.user_one_id
                        END
                        LEFT JOIN LATERAL (
                            -- 每個對話只取最新一則未刪除訊息，作為對話列表預覽。
                            SELECT m.*
                            FROM messages m
                            WHERE m.conversation_id = c.id
                              AND m.deleted_at IS NULL
                            ORDER BY m.sent_at DESC
                            LIMIT 1
                        ) lm ON TRUE
                        LEFT JOIN users lms ON lms.id = lm.sender_id
                        LEFT JOIN messages um ON um.conversation_id = c.id
                            -- 未讀數只計算別人傳的、且晚於 last_read_message_id 的訊息。
                            AND um.sender_id IS DISTINCT FROM ?
                            AND um.deleted_at IS NULL
                            AND (cp.last_read_message_id IS NULL OR um.sent_at > (
                                SELECT sent_at FROM messages WHERE id = cp.last_read_message_id
                            ))
                        WHERE cp.user_id = ?
                        GROUP BY c.id, c.type, c.group_id, g.name, g.avatar_url,
                                 du.id, du.display_name, du.avatar_url,
                                 lm.id, lm.sender_id, lms.display_name, lm.type, lm.content, lm.metadata,
                                 lm.reply_to_message_id, lm.sent_at, lm.edited_at, lm.deleted_at, c.updated_at
                        ORDER BY COALESCE(lm.sent_at, c.updated_at) DESC
                        """,
                (rs, rowNum) -> new ConversationResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getObject("group_id", UUID.class),
                        rs.getString("group_name"),
                        rs.getString("group_avatar_url"),
                        rs.getObject("direct_user_id", UUID.class),
                        rs.getString("direct_display_name"),
                        rs.getString("direct_avatar_url"),
                        rs.getObject("last_message_id", UUID.class) == null ? null : new ChatMessageResponse(
                                rs.getObject("last_message_id", UUID.class),
                                rs.getObject("id", UUID.class),
                                rs.getObject("last_sender_id", UUID.class),
                                rs.getString("last_sender_display_name"),
                                rs.getString("last_type"),
                                rs.getString("last_content"),
                                rs.getString("last_metadata"),
                                rs.getObject("last_reply_to_message_id", UUID.class),
                                rs.getObject("last_sent_at", OffsetDateTime.class),
                                rs.getObject("last_edited_at", OffsetDateTime.class),
                                rs.getObject("last_deleted_at", OffsetDateTime.class)
                        ),
                        rs.getLong("unread_count"),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                userId,
                userId,
                userId);
    }

    @Transactional
    public ConversationResponse createDirectConversation(String authorization, CreateDirectConversationRequest request) {
        UUID userId = requireUserId(authorization);
        // API 使用 username 建立私聊，這裡先轉成資料庫內部 user id。
        UUID targetUserId = findActiveUserIdByUsername(request.targetUsername())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Target user not found"));
        // 禁止建立與自己的私聊，避免產生沒有意義的 DIRECT conversation。
        if (userId.equals(targetUserId)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Cannot create a direct conversation with yourself");
        }

        // 私聊是雙人唯一的；已存在就重用，沒有才建立新的對話。
        Optional<UUID> existing = findDirectConversation(userId, targetUserId);
        UUID conversationId = existing.orElseGet(() -> insertDirectConversation(userId, targetUserId));
        return getConversation(authorization, conversationId);
    }

    @Transactional
    public ConversationResponse createGroupConversation(String authorization, CreateGroupConversationRequest request) {
        UUID ownerId = requireUserId(authorization);
        // 先建立群組主資料，再建立對應的 GROUP conversation。
        UUID groupId = jdbcTemplate.queryForObject("""
                        INSERT INTO chat_groups (name, description, avatar_url, owner_id, is_private)
                        VALUES (?, ?, ?, ?, ?)
                        RETURNING id
                        """,
                UUID.class,
                request.name().trim(),
                request.description(),
                request.avatarUrl(),
                ownerId,
                request.isPrivate() == null || request.isPrivate());

        // conversations.group_id 對應 chat_groups.id，schema 會限制一個群組只對應一個對話。
        UUID conversationId = jdbcTemplate.queryForObject("""
                        INSERT INTO conversations (type, group_id)
                        VALUES ('GROUP', ?)
                        RETURNING id
                        """,
                UUID.class,
                groupId);

        // 建立者同時是群組 OWNER 與 conversation participant。
        jdbcTemplate.update("""
                        INSERT INTO group_members (group_id, user_id, role, status, joined_at)
                        VALUES (?, ?, 'OWNER', 'ACTIVE', CURRENT_TIMESTAMP)
                        """,
                groupId,
                ownerId);
        jdbcTemplate.update("""
                        INSERT INTO conversation_participants (conversation_id, user_id)
                        VALUES (?, ?)
                        """,
                conversationId,
                ownerId);

        // 邀請的成員需要同時加入 group_members 與 conversation_participants。
        for (UUID memberId : normalizedMemberUsernames(request.memberUsernames(), ownerId)) {
            jdbcTemplate.update("""
                            INSERT INTO group_members (group_id, user_id, role, status, invited_by, joined_at)
                            VALUES (?, ?, 'MEMBER', 'ACTIVE', ?, CURRENT_TIMESTAMP)
                            ON CONFLICT (group_id, user_id) DO NOTHING
                            """,
                    groupId,
                    memberId,
                    ownerId);
            jdbcTemplate.update("""
                            INSERT INTO conversation_participants (conversation_id, user_id)
                            VALUES (?, ?)
                            ON CONFLICT (conversation_id, user_id) DO NOTHING
                            """,
                    conversationId,
                    memberId);
        }

        return getConversation(authorization, conversationId);
    }

    @Transactional
    public ConversationResponse addGroupMembers(String authorization, UUID groupId, AddGroupMembersRequest request) {
        UUID actorId = requireUserId(authorization);
        UUID conversationId = findGroupConversationId(groupId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Group not found"));
        requireGroupManager(groupId, actorId);

        for (UUID memberId : normalizedMemberUsernames(request.memberUsernames(), actorId)) {
            jdbcTemplate.update("""
                            INSERT INTO group_members (group_id, user_id, role, status, invited_by, joined_at)
                            VALUES (?, ?, 'MEMBER', 'ACTIVE', ?, CURRENT_TIMESTAMP)
                            ON CONFLICT (group_id, user_id) DO UPDATE
                            SET status = 'ACTIVE',
                                invited_by = EXCLUDED.invited_by,
                                joined_at = COALESCE(group_members.joined_at, CURRENT_TIMESTAMP),
                                updated_at = CURRENT_TIMESTAMP
                            """,
                    groupId,
                    memberId,
                    actorId);
            jdbcTemplate.update("""
                            INSERT INTO conversation_participants (conversation_id, user_id)
                            VALUES (?, ?)
                            ON CONFLICT (conversation_id, user_id) DO NOTHING
                            """,
                    conversationId,
                    memberId);
        }

        jdbcTemplate.update("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", conversationId);
        return getConversation(authorization, conversationId);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(String authorization, UUID conversationId) {
        UUID userId = requireUserId(authorization);
        // 只有對話參與者可以讀取對話摘要。
        requireParticipant(conversationId, userId);
        return listConversations(authorization).stream()
                .filter(conversation -> conversation.id().equals(conversationId))
                .findFirst()
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(String authorization, UUID conversationId, int limit, OffsetDateTime before) {
        UUID userId = requireUserId(authorization);
        requireParticipant(conversationId, userId);
        // 限制分頁大小，避免一次查詢過多訊息造成效能問題。
        int pageSize = Math.min(Math.max(limit, 1), 100);

        List<Object> params = new ArrayList<>();
        params.add(conversationId);
        String beforeFilter = "";
        if (before != null) {
            // before 用於向前翻頁，只查詢指定時間點以前的訊息。
            beforeFilter = " AND m.sent_at < ? ";
            params.add(before);
        }
        params.add(pageSize);

        return jdbcTemplate.query("""
                        SELECT m.id, m.conversation_id, m.sender_id, u.display_name AS sender_display_name,
                               m.type::text AS type, m.content, m.metadata::text AS metadata,
                               m.reply_to_message_id, m.sent_at, m.edited_at, m.deleted_at
                        FROM messages m
                        LEFT JOIN users u ON u.id = m.sender_id
                        WHERE m.conversation_id = ?
                          AND m.deleted_at IS NULL
                        """ + beforeFilter + """
                        ORDER BY m.sent_at DESC
                        LIMIT ?
                        """,
                ChatMessage.rowMapper(),
                params.toArray()).stream().map(ChatMessage::toResponse).toList();
    }

    @Transactional
    public ChatMessageResponse sendMessage(String authorization, UUID conversationId, SendMessageRequest request) {
        UUID senderId = requireUserId(authorization);
        // 發送訊息前先確認目前使用者是該對話成員。
        requireParticipant(conversationId, senderId);
        String messageType = normalizeMessageType(request.type());
        String metadata = normalizeMetadata(request.metadata());

        // 回覆訊息必須屬於同一個 conversation，避免跨對話引用。
        if (request.replyToMessageId() != null && !messageExistsInConversation(conversationId, request.replyToMessageId())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Reply message does not belong to this conversation");
        }

        ChatMessage message = jdbcTemplate.queryForObject("""
                        INSERT INTO messages (conversation_id, sender_id, type, content, metadata, reply_to_message_id)
                        VALUES (?, ?, ?::message_type, ?, CAST(? AS jsonb), ?)
                        RETURNING id, conversation_id, sender_id,
                                  (SELECT display_name FROM users WHERE id = sender_id) AS sender_display_name,
                                  type::text AS type, content, metadata::text AS metadata,
                                  reply_to_message_id, sent_at, edited_at, deleted_at
                        """,
                ChatMessage.rowMapper(),
                conversationId,
                senderId,
                messageType,
                request.content().trim(),
                metadata,
                request.replyToMessageId());

        // 更新 conversation.updated_at，讓對話列表能依最新活動排序。
        jdbcTemplate.update("UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", conversationId);
        // 寄件者自然已讀自己發出的訊息，因此同步寫入 message_reads 與 last_read_message_id。
        jdbcTemplate.update("""
                        INSERT INTO message_reads (message_id, user_id)
                        VALUES (?, ?)
                        ON CONFLICT (message_id, user_id) DO NOTHING
                        """,
                message.id(),
                senderId);
        jdbcTemplate.update("""
                        UPDATE conversation_participants
                        SET last_read_message_id = ?
                        WHERE conversation_id = ?
                          AND user_id = ?
                        """,
                message.id(),
                conversationId,
                senderId);

        return message.toResponse();
    }

    @Transactional
    public ChatMessageResponse markRead(String authorization, UUID conversationId, MarkReadRequest request) {
        UUID userId = requireUserId(authorization);
        requireParticipant(conversationId, userId);
        // 先確認 message 存在且屬於這個 conversation。
        ChatMessage message = findMessage(conversationId, request.messageId())
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Message not found"));

        // message_reads 保存每則訊息的讀取紀錄，participants 則保存快速計算未讀數的游標。
        jdbcTemplate.update("""
                        INSERT INTO message_reads (message_id, user_id)
                        VALUES (?, ?)
                        ON CONFLICT (message_id, user_id) DO UPDATE SET read_at = CURRENT_TIMESTAMP
                        """,
                request.messageId(),
                userId);
        jdbcTemplate.update("""
                        UPDATE conversation_participants
                        SET last_read_message_id = ?
                        WHERE conversation_id = ?
                          AND user_id = ?
                        """,
                request.messageId(),
                conversationId,
                userId);

        return message.toResponse();
    }

    private UUID requireUserId(String authorization) {
        return sessionAuthService.requireActiveSession(authorization).userId();
    }

    private void requireActiveUser(UUID userId) {
        // 確認使用者仍為 ACTIVE；保留給需要額外檢查成員狀態的流程使用。
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM users WHERE id = ? AND status = 'ACTIVE'
                        )
                        """,
                Boolean.class,
                userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AuthException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private Optional<UUID> findActiveUserIdByUsername(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT id
                            FROM users
                            WHERE LOWER(username) = LOWER(?)
                              AND status = 'ACTIVE'
                            """,
                    UUID.class,
                    username.trim()));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private void requireParticipant(UUID conversationId, UUID userId) {
        // 權限檢查：只有 conversation_participants 裡的人能讀寫該對話。
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM conversation_participants
                            WHERE conversation_id = ? AND user_id = ?
                        )
                        """,
                Boolean.class,
                conversationId,
                userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "You are not a participant in this conversation");
        }
    }

    private void requireGroupManager(UUID groupId, UUID userId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM group_members
                            WHERE group_id = ?
                              AND user_id = ?
                              AND status = 'ACTIVE'
                              AND role IN ('OWNER', 'ADMIN')
                        )
                        """,
                Boolean.class,
                groupId,
                userId);
        if (!Boolean.TRUE.equals(exists)) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Only group owners or admins can add members");
        }
    }

    private Optional<UUID> findGroupConversationId(UUID groupId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT id
                            FROM conversations
                            WHERE group_id = ?
                              AND type = 'GROUP'
                            """,
                    UUID.class,
                    groupId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private Optional<UUID> findDirectConversation(UUID userId, UUID targetUserId) {
        try {
            // LEAST/GREATEST 讓 A-B 與 B-A 視為同一組私聊。
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT conversation_id
                            FROM direct_conversations
                            WHERE LEAST(user_one_id, user_two_id) = LEAST(?::uuid, ?::uuid)
                              AND GREATEST(user_one_id, user_two_id) = GREATEST(?::uuid, ?::uuid)
                            """,
                    UUID.class,
                    userId,
                    targetUserId,
                    userId,
                    targetUserId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private UUID insertDirectConversation(UUID userId, UUID targetUserId) {
        // DIRECT conversation 不需要 group_id。
        UUID conversationId = jdbcTemplate.queryForObject("""
                        INSERT INTO conversations (type)
                        VALUES ('DIRECT')
                        RETURNING id
                        """,
                UUID.class);

        // 固定 user_one_id/user_two_id 順序，配合 unique index 避免重複私聊。
        jdbcTemplate.update("""
                        INSERT INTO direct_conversations (conversation_id, user_one_id, user_two_id)
                        VALUES (?, LEAST(?::uuid, ?::uuid), GREATEST(?::uuid, ?::uuid))
                        """,
                conversationId,
                userId,
                targetUserId,
                userId,
                targetUserId);
        jdbcTemplate.update("""
                        INSERT INTO conversation_participants (conversation_id, user_id)
                        VALUES (?, ?), (?, ?)
                        """,
                conversationId,
                userId,
                conversationId,
                targetUserId);

        return conversationId;
    }

    private Set<UUID> normalizedMemberUsernames(Set<String> memberUsernames, UUID ownerId) {
        // 將邀請名單從 username 轉成 user id，並排除建立者自己。
        if (memberUsernames == null || memberUsernames.isEmpty()) {
            return Set.of();
        }
        return memberUsernames.stream()
                .map(username -> findActiveUserIdByUsername(username)
                        .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "Group member not found: " + username)))
                .filter(memberId -> !ownerId.equals(memberId))
                .collect(java.util.stream.Collectors.toSet());
    }

    private String normalizeMessageType(String type) {
        // 空值預設為 TEXT，其餘型別統一轉大寫後檢查白名單。
        String messageType = type == null || type.isBlank() ? "TEXT" : type.trim().toUpperCase(Locale.ROOT);
        if (!MESSAGE_TYPES.contains(messageType)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Unsupported message type");
        }
        return messageType;
    }

    private String normalizeMetadata(String metadata) {
        // metadata 存入 JSONB；空值一律以空物件表示。
        if (metadata == null || metadata.isBlank()) {
            return "{}";
        }
        try {
            // 先 parse 再 serialize，確保輸入是合法 JSON 且格式一致。
            Object parsed = objectMapper.readValue(metadata, Object.class);
            return objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException exception) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Metadata must be valid JSON");
        }
    }

    private boolean messageExistsInConversation(UUID conversationId, UUID messageId) {
        // 驗證回覆目標是否存在於同一對話，且尚未被刪除。
        Boolean exists = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM messages
                            WHERE id = ?
                              AND conversation_id = ?
                              AND deleted_at IS NULL
                        )
                        """,
                Boolean.class,
                messageId,
                conversationId);
        return Boolean.TRUE.equals(exists);
    }

    private Optional<ChatMessage> findMessage(UUID conversationId, UUID messageId) {
        try {
            // markRead 需要回傳完整訊息資料，因此查詢時一起帶出 sender display name。
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT m.id, m.conversation_id, m.sender_id, u.display_name AS sender_display_name,
                                   m.type::text AS type, m.content, m.metadata::text AS metadata,
                                   m.reply_to_message_id, m.sent_at, m.edited_at, m.deleted_at
                            FROM messages m
                            LEFT JOIN users u ON u.id = m.sender_id
                            WHERE m.id = ?
                              AND m.conversation_id = ?
                              AND m.deleted_at IS NULL
                            """,
                    ChatMessage.rowMapper(),
                    messageId,
                    conversationId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }
}
