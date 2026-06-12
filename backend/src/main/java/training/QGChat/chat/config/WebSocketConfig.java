package training.QGChat.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 啟用簡易訊息代理，前端可訂閱 /topic/... 接收聊天室推播。
        registry.enableSimpleBroker("/topic");
        // 前端送到 /app/... 的訊息會交給 @MessageMapping 處理。
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 原生 WebSocket 連線入口，方便 Postman 或其他 WebSocket 工具測試 STOMP frame。
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");

        // SockJS 連線入口；讓不支援原生 WebSocket 的前端環境也能 fallback。
        registry.addEndpoint("/ws/chat-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
