package com.memospace.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Process-local registry for authenticated realtime sessions.
 *
 * <p>The public {@link #sendToUser(long, String, Object)} and
 * {@link #isOnline(long)} methods are intentionally transport-agnostic so
 * notification producers do not need to depend on the chat handler.</p>
 */
@Component
public class RealtimeGateway {
    private static final Logger log = LoggerFactory.getLogger(RealtimeGateway.class);
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 256 * 1024;

    private final ObjectMapper json;
    private final ConcurrentMap<Long, ConcurrentMap<String, WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WebSocketSession> safeSessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> sessionOwners = new ConcurrentHashMap<>();

    public RealtimeGateway(ObjectMapper json) {
        this.json = json;
    }

    /** @return true when this is the user's first live session. */
    public boolean register(long userId, WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        safeSessions.put(session.getId(), safe);
        sessionOwners.put(session.getId(), userId);
        ConcurrentMap<String, WebSocketSession> sessions =
                userSessions.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>());
        sessions.put(session.getId(), safe);
        return sessions.size() == 1;
    }

    /** @return true when the user no longer has any live sessions. */
    public boolean unregister(long userId, WebSocketSession session) {
        removeSession(userId, session.getId());
        return !isOnline(userId);
    }

    public boolean isOnline(long userId) {
        ConcurrentMap<String, WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return false;
        sessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        if (sessions.isEmpty()) {
            userSessions.remove(userId, sessions);
            return false;
        }
        return true;
    }

    /**
     * Sends an event to every live session belonging to a user. Map payloads
     * are flattened into the event; other payloads are placed under
     * {@code payload}.
     */
    public void sendToUser(long userId, String type, Object payload) {
        sendToUser(userId, event(type, payload));
    }

    public void sendToUser(long userId, Map<String, ?> event) {
        ConcurrentMap<String, WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return;
        sessions.forEach((sessionId, session) -> {
            if (!write(session, event)) removeSession(userId, sessionId);
        });
    }

    public void sendToSession(WebSocketSession session, String type, Object payload) {
        sendToSession(session, event(type, payload));
    }

    public void sendToSession(WebSocketSession session, Map<String, ?> event) {
        WebSocketSession target = safeSessions.getOrDefault(session.getId(), session);
        if (!write(target, event)) {
            Long owner = sessionOwners.get(session.getId());
            if (owner != null) removeSession(owner, session.getId());
        }
    }

    private Map<String, Object> event(String type, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        if (payload instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null && !"type".equals(String.valueOf(key))) {
                    event.put(String.valueOf(key), value);
                }
            });
        } else if (payload != null) {
            event.put("payload", payload);
        }
        return event;
    }

    private boolean write(WebSocketSession session, Map<String, ?> event) {
        if (!session.isOpen()) return false;
        try {
            session.sendMessage(new TextMessage(serialize(event)));
            return true;
        } catch (IOException | RuntimeException ex) {
            log.debug("Realtime delivery failed for session {}", session.getId(), ex);
            return false;
        }
    }

    private String serialize(Map<String, ?> event) throws JsonProcessingException {
        return json.writeValueAsString(event);
    }

    private void removeSession(long userId, String sessionId) {
        safeSessions.remove(sessionId);
        sessionOwners.remove(sessionId);
        ConcurrentMap<String, WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return;
        sessions.remove(sessionId);
        if (sessions.isEmpty()) userSessions.remove(userId, sessions);
    }
}
