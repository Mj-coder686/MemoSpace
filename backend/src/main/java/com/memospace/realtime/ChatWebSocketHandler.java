package com.memospace.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memospace.api.ApiException;
import com.memospace.api.ChatController;
import com.memospace.security.JwtService;
import com.memospace.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final ObjectMapper json;
    private final JwtService jwt;
    private final ChatService chat;
    private final RealtimeGateway realtime;
    private final ConcurrentMap<String, Long> authenticatedUsers = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ObjectMapper json, JwtService jwt, ChatService chat, RealtimeGateway realtime) {
        this.json = json;
        this.jwt = jwt;
        this.chat = chat;
        this.realtime = realtime;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode frame;
        try {
            frame = json.readTree(message.getPayload());
        } catch (Exception ex) {
            if (!authenticatedUsers.containsKey(session.getId())) {
                rejectAuthentication(session, "首条消息必须是有效的 AUTH JSON");
            } else {
                error(session, "INVALID_PAYLOAD", "消息必须是有效的 JSON", null);
            }
            return;
        }
        if (frame == null || !frame.isObject()) {
            if (!authenticatedUsers.containsKey(session.getId())) {
                rejectAuthentication(session, "首条消息必须是 AUTH 对象");
            } else {
                error(session, "INVALID_PAYLOAD", "消息必须是 JSON 对象", null);
            }
            return;
        }

        Long userId = authenticatedUsers.get(session.getId());
        if (userId == null) {
            authenticate(session, frame);
            return;
        }

        String type = text(frame, "type");
        try {
            switch (type) {
                case "CHAT_SEND" -> chatSend(session, userId, frame);
                case "READ" -> read(userId, frame);
                case "PING" -> pong(session, frame);
                case "AUTH" -> error(session, "ALREADY_AUTHENTICATED", "连接已经完成认证", null);
                default -> error(session, "UNSUPPORTED_TYPE", "不支持的消息类型", clientMessageId(frame));
            }
        } catch (ApiException ex) {
            error(session, ex.getStatus().name(), ex.getMessage(), clientMessageId(frame));
        } catch (IllegalArgumentException ex) {
            error(session, "INVALID_PAYLOAD", ex.getMessage(), clientMessageId(frame));
        } catch (Exception ex) {
            log.error("Unexpected realtime chat error for user {}", userId, ex);
            error(session, "INTERNAL_ERROR", "消息处理失败，请稍后重试", clientMessageId(frame));
        }
    }

    private void authenticate(WebSocketSession session, JsonNode frame) throws IOException {
        if (!"AUTH".equals(text(frame, "type"))) {
            rejectAuthentication(session, "首条消息必须是 AUTH");
            return;
        }
        String token = text(frame, "token");
        if (token.isBlank()) {
            rejectAuthentication(session, "AUTH 必须携带 token");
            return;
        }

        try {
            long userId = jwt.parseUserId(token);
            authenticatedUsers.put(session.getId(), userId);
            boolean becameOnline = realtime.register(userId, session);
            List<Long> onlineFriendIds = chat.activeFriendIds(userId).stream()
                    .filter(realtime::isOnline)
                    .toList();
            realtime.sendToSession(session, "AUTH_OK", Map.of(
                    "userId", userId,
                    "onlineFriendIds", onlineFriendIds));
            if (becameOnline) announcePresence(userId, true);
        } catch (Exception ex) {
            authenticatedUsers.remove(session.getId());
            rejectAuthentication(session, "token 无效或已过期");
        }
    }

    private void chatSend(WebSocketSession session, long senderId, JsonNode frame) {
        long friendId = positiveLong(frame, "friendId", "receiverId");
        String clientMessageId = requiredText(frame, "clientMessageId");
        String content = requiredText(frame, "content");

        ChatService.SendResult result = chat.send(senderId, friendId, clientMessageId, content);
        ChatService.ChatMessage stored = result.message();
        if (!result.duplicate() && realtime.isOnline(friendId)) {
            stored = chat.markDelivered(stored.id());
        }

        realtime.sendToSession(session, "ACK", Map.of(
                "clientMessageId", stored.clientMessageId(),
                "messageId", stored.id(),
                "sentAt", stored.sentAt(),
                "duplicate", result.duplicate()));
        if (!result.duplicate()) {
            realtime.sendToUser(friendId, "MESSAGE", Map.of("message", stored));
        }
    }

    private void read(long readerId, JsonNode frame) {
        long friendId = positiveLong(frame, "friendId", null);
        long throughMessageId = positiveLong(frame, "throughMessageId", "messageId");
        ChatService.ReadReceipt receipt = chat.markRead(readerId, friendId, throughMessageId);
        Map<String, Object> event = ChatController.readEvent(receipt);
        realtime.sendToUser(readerId, "READ", event);
        realtime.sendToUser(friendId, "READ", event);
    }

    private void pong(WebSocketSession session, JsonNode frame) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (frame.hasNonNull("nonce")) payload.put("nonce", frame.get("nonce"));
        payload.put("timestamp", Instant.now().toString());
        realtime.sendToSession(session, "PONG", payload);
    }

    private void announcePresence(long userId, boolean online) {
        Map<String, Object> event = Map.of("userId", userId, "online", online);
        for (Long friendId : chat.activeFriendIds(userId)) {
            realtime.sendToUser(friendId, "PRESENCE", event);
        }
    }

    private void rejectAuthentication(WebSocketSession session, String message) throws IOException {
        error(session, "AUTH_REQUIRED", message, null);
        if (session.isOpen()) session.close(new CloseStatus(4401, "Authentication required"));
    }

    private void error(WebSocketSession session, String code, String message, String clientMessageId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("code", code);
        event.put("message", message);
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            event.put("clientMessageId", clientMessageId);
        }
        realtime.sendToSession(session, "ERROR", event);
    }

    private String requiredText(JsonNode frame, String field) {
        String value = text(frame, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value;
    }

    private long positiveLong(JsonNode frame, String field, String alias) {
        JsonNode value = frame.get(field);
        if ((value == null || value.isNull()) && alias != null) value = frame.get(alias);
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException(field + " 必须为正数");
        }
        return value.asLong();
    }

    private String clientMessageId(JsonNode frame) {
        String value = text(frame, "clientMessageId");
        return value.isBlank() ? null : value;
    }

    private String text(JsonNode frame, String field) {
        JsonNode value = frame.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = authenticatedUsers.remove(session.getId());
        if (userId != null && realtime.unregister(userId, session)) {
            announcePresence(userId, false);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.debug("Realtime transport error for session {}", session.getId(), exception);
        if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
    }
}
