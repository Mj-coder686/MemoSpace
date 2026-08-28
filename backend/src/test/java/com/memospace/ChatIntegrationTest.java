package com.memospace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memospace.service.JdbcIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTest {
    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void authenticatesPersistsDeduplicatesDeliversReadsAndPagesMessages() throws Exception {
        User alice = register("chat_alice_" + suffix());
        User bob = register("chat_bob_" + suffix());
        User inactive = register("chat_inactive_" + suffix());
        JdbcIds.insert(jdbc,
                "INSERT INTO friendship(user_low_id,user_high_id,status) VALUES(?,?,'ACTIVE')",
                Math.min(alice.id(), bob.id()), Math.max(alice.id(), bob.id()));
        JdbcIds.insert(jdbc,
                "INSERT INTO friendship(user_low_id,user_high_id,status,ended_at) VALUES(?,?,'ENDED',CURRENT_TIMESTAMP)",
                Math.min(alice.id(), inactive.id()), Math.max(alice.id(), inactive.id()));

        List<WebSocketSession> sessions = new ArrayList<>();
        try {
            SocketEvents aliceEvents = new SocketEvents(json);
            WebSocketSession aliceSocket = connect(aliceEvents);
            sessions.add(aliceSocket);
            send(aliceSocket, Map.of("type", "AUTH", "token", alice.token()));
            JsonNode aliceAuth = aliceEvents.await("AUTH_OK");
            assertEquals(alice.id(), aliceAuth.get("userId").asLong());
            assertTrue(aliceAuth.get("onlineFriendIds").isEmpty());

            SocketEvents bobEvents = new SocketEvents(json);
            WebSocketSession bobSocket = connect(bobEvents);
            sessions.add(bobSocket);
            send(bobSocket, Map.of("type", "AUTH", "token", bob.token()));
            JsonNode bobAuth = bobEvents.await("AUTH_OK");
            assertEquals(alice.id(), bobAuth.at("/onlineFriendIds/0").asLong());
            JsonNode online = aliceEvents.await("PRESENCE");
            assertEquals(bob.id(), online.get("userId").asLong());
            assertTrue(online.get("online").asBoolean());

            String clientMessageId = "client-" + UUID.randomUUID();
            send(aliceSocket, Map.of(
                    "type", "CHAT_SEND",
                    "friendId", bob.id(),
                    "clientMessageId", clientMessageId,
                    "content", "第一条实时消息"));
            JsonNode ack = aliceEvents.await("ACK");
            JsonNode delivery = bobEvents.await("MESSAGE");
            long messageId = ack.get("messageId").asLong();
            assertFalse(ack.get("duplicate").asBoolean());
            assertEquals(messageId, delivery.at("/message/id").asLong());
            assertEquals("第一条实时消息", delivery.at("/message/content").asText());
            assertFalse(delivery.at("/message/deliveredAt").isMissingNode());

            send(aliceSocket, Map.of(
                    "type", "CHAT_SEND",
                    "receiverId", bob.id(),
                    "clientMessageId", clientMessageId,
                    "content", "重复重试不应新增记录"));
            JsonNode duplicateAck = aliceEvents.await("ACK");
            assertTrue(duplicateAck.get("duplicate").asBoolean());
            assertEquals(messageId, duplicateAck.get("messageId").asLong());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM direct_message WHERE sender_id=? AND client_message_id=?",
                    Integer.class, alice.id(), clientMessageId));

            String historyBody = mvc.perform(get("/api/friends/{friendId}/messages", bob.id())
                            .header("Authorization", bearer(alice.token()))
                            .param("limit", "20"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode history = json.readTree(historyBody);
            assertEquals(1, history.get("items").size());
            assertEquals(messageId, history.at("/items/0/id").asLong());
            assertFalse(history.get("hasMore").asBoolean());

            send(bobSocket, Map.of(
                    "type", "READ",
                    "friendId", alice.id(),
                    "throughMessageId", messageId));
            JsonNode bobRead = bobEvents.await("READ");
            JsonNode aliceRead = aliceEvents.await("READ");
            assertEquals(bob.id(), aliceRead.get("userId").asLong());
            assertEquals(messageId, aliceRead.get("throughMessageId").asLong());
            assertEquals(messageId, bobRead.get("throughMessageId").asLong());
            assertNotNull(jdbc.queryForObject(
                    "SELECT read_at FROM direct_message WHERE id=?", Object.class, messageId));

            send(aliceSocket, Map.of("type", "PING", "nonce", "n-1"));
            assertEquals("n-1", aliceEvents.await("PONG").get("nonce").asText());

            send(aliceSocket, Map.of(
                    "type", "CHAT_SEND",
                    "friendId", inactive.id(),
                    "clientMessageId", "forbidden-" + UUID.randomUUID(),
                    "content", "非 ACTIVE 好友不可发送"));
            assertEquals("FORBIDDEN", aliceEvents.await("ERROR").get("code").asText());
        } finally {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) session.close(CloseStatus.NORMAL);
            }
        }
    }

    @Test
    void rejectsAnyFirstFrameThatIsNotAuth() throws Exception {
        SocketEvents events = new SocketEvents(json);
        WebSocketSession session = connect(events);
        try {
            send(session, Map.of("type", "PING"));
            JsonNode error = events.await("ERROR");
            assertEquals("AUTH_REQUIRED", error.get("code").asText());
            assertTrue(events.awaitClosed(Duration.ofSeconds(5)));
        } finally {
            if (session.isOpen()) session.close(CloseStatus.NORMAL);
        }
    }

    @Test
    void restReadEndpointUpdatesIncomingMessagesAndPublishesReceiptShape() throws Exception {
        User sender = register("read_sender_" + suffix());
        User reader = register("read_reader_" + suffix());
        long friendshipId = JdbcIds.insert(jdbc,
                "INSERT INTO friendship(user_low_id,user_high_id,status) VALUES(?,?,'ACTIVE')",
                Math.min(sender.id(), reader.id()), Math.max(sender.id(), reader.id()));
        long messageId = JdbcIds.insert(jdbc,
                "INSERT INTO direct_message(friendship_id,sender_id,receiver_id,client_message_id,content) VALUES(?,?,?,?,?)",
                friendshipId, sender.id(), reader.id(), "rest-read-" + UUID.randomUUID(), "稍后读取");

        String body = mvc.perform(post("/api/friends/{friendId}/messages/read", sender.id())
                        .header("Authorization", bearer(reader.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("throughMessageId", messageId))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode receipt = json.readTree(body);
        assertEquals(reader.id(), receipt.get("userId").asLong());
        assertEquals(sender.id(), receipt.get("friendId").asLong());
        assertEquals(1, receipt.get("updatedCount").asInt());
        assertNotNull(jdbc.queryForObject("SELECT read_at FROM direct_message WHERE id=?", Object.class, messageId));
    }

    private User register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Memo123!",
                                "nickname", username))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);
        return new User(response.at("/user/id").asLong(), response.get("token").asText());
    }

    private WebSocketSession connect(SocketEvents events) throws Exception {
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:5173");
        return client.execute(events, headers, URI.create("ws://localhost:" + port + "/ws/chat"))
                .get(10, TimeUnit.SECONDS);
    }

    private void send(WebSocketSession session, Object frame) throws Exception {
        session.sendMessage(new TextMessage(json.writeValueAsString(frame)));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record User(long id, String token) {}

    private static final class SocketEvents extends TextWebSocketHandler {
        private final ObjectMapper json;
        private final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        private final BlockingQueue<CloseStatus> closes = new LinkedBlockingQueue<>();

        private SocketEvents(ObjectMapper json) {
            this.json = json;
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
            events.add(json.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closes.add(status);
        }

        private JsonNode await(String type) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (System.nanoTime() < deadline) {
                long remaining = deadline - System.nanoTime();
                JsonNode event = events.poll(Math.max(1, remaining), TimeUnit.NANOSECONDS);
                if (event == null) break;
                if (type.equals(event.path("type").asText())) return event;
            }
            throw new AssertionError("Timed out waiting for " + type + "; queued events=" + events);
        }

        private boolean awaitClosed(Duration timeout) throws InterruptedException {
            return closes.poll(timeout.toMillis(), TimeUnit.MILLISECONDS) != null;
        }
    }
}
