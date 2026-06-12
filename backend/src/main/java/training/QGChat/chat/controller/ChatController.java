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
        // 取得目前登入者參與的所有聊天室，包含最後一則訊息與未讀數。
        return chatService.listConversations(authorization);
    }

    @PostMapping("/direct")
    public ResponseEntity<ConversationResponse> createDirectConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateDirectConversationRequest request
    ) {
        // 建立或取得兩人既有的一對一聊天室，避免重複開同一組對話。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createDirectConversation(authorization, request));
    }

    @PostMapping("/groups")
    public ResponseEntity<ConversationResponse> createGroupConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateGroupConversationRequest request
    ) {
        // 建立群組與對應的 GROUP conversation，並把指定成員加入參與者清單。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createGroupConversation(authorization, request));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessageResponse> listMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before
    ) {
        // 以 sent_at 做游標分頁；before 有值時只取該時間之前的訊息。
        return chatService.listMessages(authorization, conversationId, limit, before);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request
    ) {
        ChatMessageResponse message = chatService.sendMessage(authorization, conversationId, request);
        // REST 送訊息成功後，也同步推播給訂閱同一聊天室 topic 的 WebSocket client。
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
        // 更新單一使用者在此聊天室的最後已讀訊息。
        return chatService.markRead(authorization, conversationId, request);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handleAuthException(AuthException exception) {
        // 將聊天功能內的授權與商業邏輯錯誤統一轉成 JSON 回應。
        return ResponseEntity.status(exception.status())
                .body(Map.of("message", exception.getMessage()));
    }
}
