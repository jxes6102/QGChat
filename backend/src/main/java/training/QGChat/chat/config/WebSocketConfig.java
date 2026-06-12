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
        // 啟用簡單訊息 broker，client 可訂閱 /topic/... 接收對話事件。
        registry.enableSimpleBroker("/topic");
        // client 發到 /app/... 的訊息會路由到 @MessageMapping 方法。
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 原生 WebSocket STOMP 端點，適合 Postman 或支援 WebSocket 的前端使用。
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*");

        // SockJS 端點，提供不支援原生 WebSocket 環境的 fallback。
        registry.addEndpoint("/ws/chat-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
