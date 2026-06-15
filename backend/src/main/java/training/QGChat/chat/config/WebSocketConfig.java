package training.QGChat.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            @Value("${qgchat.websocket.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String allowedOriginPatterns
    ) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

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
                .setAllowedOriginPatterns(allowedOriginPatterns);

        // SockJS 端點，提供不支援原生 WebSocket 環境的 fallback。
        registry.addEndpoint("/ws/chat-sockjs")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
