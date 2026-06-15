package training.QGChat.chat.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.chat.config.StompAuthChannelInterceptor;
import training.QGChat.chat.dto.ChatMessageResponse;
import training.QGChat.chat.dto.WebSocketSendMessageRequest;
import training.QGChat.chat.service.ChatService;

import java.util.List;
import java.util.Map;

@Controller
public class ChatSocketController {
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatSocketController(ChatService chatService, SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void sendMessage(
            @Valid @Payload WebSocketSendMessageRequest request,
            @Headers Map<String, Object> headers
    ) {
        // WebSocket client 發送到 /app/chat.send 後，沿用 REST 相同的 service 寫入訊息。
        ChatMessageResponse message = chatService.sendMessage(
                authorization(headers),
                request.conversationId(),
                request.toSendMessageRequest()
        );
        // 寫入成功後廣播到對話 topic，讓所有訂閱者即時收到新訊息。
        messagingTemplate.convertAndSend("/topic/conversations/" + request.conversationId(), message);
    }

    @MessageExceptionHandler
    public Map<String, String> handleException(Exception exception) {
        // STOMP handler 的錯誤也回傳簡單 JSON，方便 client 顯示。
        return Map.of("message", exception.getMessage());
    }

    private String authorization(Map<String, Object> headers) {
        String nativeAuthorization = nativeAuthorization(headers);
        if (nativeAuthorization != null && !nativeAuthorization.isBlank()) {
            return nativeAuthorization;
        }

        Object sessionAttributes = headers.get(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES);
        if (sessionAttributes instanceof Map<?, ?> attributes) {
            Object authorization = attributes.get(StompAuthChannelInterceptor.AUTHORIZATION_SESSION_ATTRIBUTE);
            if (authorization instanceof String value && !value.isBlank()) {
                return value;
            }
        }

        throw new AuthException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
    }

    private String nativeAuthorization(Map<String, Object> headers) {
        Object nativeHeaders = headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
        if (!(nativeHeaders instanceof Map<?, ?> headerMap)) {
            return null;
        }

        Object values = headerMap.get("Authorization");
        if (values instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof String value) {
            return value;
        }
        if (values instanceof String value) {
            return value;
        }
        return null;
    }
}
