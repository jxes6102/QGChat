package training.QGChat.chat.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.chat.dto.AddGroupMembersRequest;
import training.QGChat.chat.dto.ChatMessageResponse;
import training.QGChat.chat.dto.ConversationResponse;
import training.QGChat.chat.dto.CreateDirectConversationRequest;
import training.QGChat.chat.dto.CreateGroupConversationRequest;
import training.QGChat.chat.dto.MarkReadRequest;
import training.QGChat.chat.dto.SendMessageRequest;
import training.QGChat.chat.service.ChatService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chats")
public class ChatController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> listConversations(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        // 讀取目前登入者的對話列表，包含最後訊息與未讀數。
        return chatService.listConversations(authorization);
    }

    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> createDirectConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateDirectConversationRequest request
    ) {
        // 建立或取得與指定 username 的一對一私聊。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createDirectConversation(authorization, request));
    }

    @PostMapping("/groups")
    public ResponseEntity<ConversationResponse> createGroupConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateGroupConversationRequest request
    ) {
        // 建立群組與對應的 GROUP conversation，並加入初始成員。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createGroupConversation(authorization, request));
    }

    @PostMapping("/groups/{groupId}/members")
    public ConversationResponse addGroupMembers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID groupId,
            @Valid @RequestBody AddGroupMembersRequest request
    ) {
        return chatService.addGroupMembers(authorization, groupId, request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageResponse> listMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before
    ) {
        // 使用 before 參數向前分頁，取得指定時間以前的訊息。
        return chatService.listMessages(authorization, conversationId, limit, before);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        ChatMessageResponse message = chatService.sendMessage(authorization, conversationId, request);
        // REST 發送成功後，同步廣播到該 conversation 的 WebSocket topic。
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, message);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(message);
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ChatMessageResponse markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID conversationId,
            @Valid @RequestBody MarkReadRequest request
    ) {
        // 更新目前使用者在這個對話中的最後已讀訊息。
        return chatService.markRead(authorization, conversationId, request);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException exception) {
        // 將聊天相關權限與驗證錯誤統一轉成前端可讀的 JSON。
        return ResponseEntity.status(exception.status())
                .body(Map.of("message", exception.getMessage()));
    }
}
