package training.QGChat.chat.config;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import training.QGChat.auth.exception.AuthException;
import training.QGChat.auth.service.SessionAuthService;

import java.util.HashMap;
import java.util.Map;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    public static final String AUTHORIZATION_SESSION_ATTRIBUTE = "authorization";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final SessionAuthService sessionAuthService;

    public StompAuthChannelInterceptor(SessionAuthService sessionAuthService) {
        this.sessionAuthService = sessionAuthService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
            sessionAuthService.requireActiveSession(authorization);
            sessionAttributes(accessor).put(AUTHORIZATION_SESSION_ATTRIBUTE, authorization);
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            String authorization = authorizationFromSendOrSession(accessor);
            sessionAuthService.requireActiveSession(authorization);
        }

        return message;
    }

    private String authorizationFromSendOrSession(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (authorization != null && !authorization.isBlank()) {
            return authorization;
        }

        Object sessionAuthorization = sessionAttributes(accessor).get(AUTHORIZATION_SESSION_ATTRIBUTE);
        if (sessionAuthorization instanceof String value && !value.isBlank()) {
            return value;
        }

        throw new AuthException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
    }

    private Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
            accessor.setSessionAttributes(attributes);
        }
        return attributes;
    }
}
