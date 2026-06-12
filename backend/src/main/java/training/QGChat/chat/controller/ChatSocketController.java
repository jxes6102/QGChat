package training.QGChat.chat.controller;

import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import training.QGChat.chat.dto.ChatMessageResponse;
import training.QGChat.chat.dto.WebSocketSendMessageRequest;
import training.QGChat.chat.service.ChatService;

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
    public void sendMessage(@Valid @Payload WebSocketSendMessageRequest request) {
        // 前端送到 /app/chat.send 時，先走與 REST 相同的 service 寫入資料庫。
        ChatMessageResponse message = chatService.sendMessage(
                request.authorization(),
                request.conversationId(),
                request.toSendMessageRequest()
        );
        // 寫入成功後，廣播到該聊天室的 topic，所有訂閱者都會收到新訊息。
        messagingTemplate.convertAndSend("/topic/conversations/" + request.conversationId(), message);
    }

    @MessageExceptionHandler
    public Map<String, String> handleException(Exception exception) {
        // STOMP handler 內的錯誤用簡單 JSON 形式回傳給 client。
        return Map.of("message", exception.getMessage());
    }
}
