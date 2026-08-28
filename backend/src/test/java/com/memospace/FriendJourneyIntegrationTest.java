package com.memospace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendJourneyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void numericMemoIdAndFullFriendLifecycle() throws Exception {
        JsonNode alice = register("friend_alice", "好友甲");
        JsonNode bob = register("friend_bob", "好友乙");
        JsonNode outsider = register("friend_outsider", "越权者");
        String aliceToken = alice.get("token").asText();
        String bobToken = bob.get("token").asText();
        String outsiderToken = outsider.get("token").asText();
        long aliceId = alice.at("/user/id").asLong();
        long bobId = bob.at("/user/id").asLong();
        String aliceMemoId = alice.at("/user/public_id").asText();
        String bobMemoId = bob.at("/user/public_id").asText();

        assertTrue(aliceMemoId.matches("\\d{12}"));
        assertTrue(bobMemoId.matches("\\d{12}"));
        assertNotEquals(aliceMemoId, bobMemoId);

        JsonNode exactSearch = getJson("/api/users/search?q=" + bobMemoId, aliceToken, 200);
        assertEquals(1, exactSearch.size());
        assertEquals(bobMemoId, exactSearch.get(0).get("public_id").asText());
        assertEquals("NONE", exactSearch.get(0).get("friend_state").asText());

        JsonNode request = postJson("/api/friends/requests",
                Map.of("receiverId", bobId, "message", "一起保存生活吧"), aliceToken, 200);
        long requestId = request.get("id").asLong();
        JsonNode bobRequests = getJson("/api/friends/requests", bobToken, 200);
        assertEquals("INCOMING", bobRequests.get(0).get("direction").asText());
        postJson("/api/friends/requests/" + requestId + "/accept", Map.of(), outsiderToken, 403);

        JsonNode accepted = postJson("/api/friends/requests/" + requestId + "/accept", Map.of(), bobToken, 200);
        assertEquals("ACCEPTED", accepted.get("status").asText());
        assertEquals(bobId, getJson("/api/friends", aliceToken, 200).get(0).get("friend_id").asLong());
        assertEquals(aliceId, getJson("/api/friends", bobToken, 200).get(0).get("friend_id").asLong());
        postJson("/api/friends/requests", Map.of("receiverId", bobId), aliceToken, 409);

        JsonNode settings = putJson("/api/friends/" + aliceId + "/settings",
                Map.of("remarkName", "甲同学", "allowDirectReminders", false, "muteChat", true), bobToken, 200);
        assertEquals("甲同学", settings.get("remark_name").asText());
        assertFalse(settings.get("allow_direct_reminders").asBoolean());
        assertTrue(settings.get("mute_chat").asBoolean());

        deleteJson("/api/friends/" + bobId, aliceToken, 200);
        assertEquals(0, getJson("/api/friends", aliceToken, 200).size());
        postJson("/api/friends/requests", Map.of("receiverId", bobId, "message", "重新加回"), aliceToken, 200);
    }

    private JsonNode register(String username, String nickname) throws Exception {
        return postJson("/api/auth/register", Map.of(
                "username", username, "password", "Memo123!", "nickname", nickname), null, 200);
    }

    private JsonNode postJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String value = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return value.isBlank() ? json.createObjectNode() : json.readTree(value);
    }

    private JsonNode putJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = put(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String value = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return value.isBlank() ? json.createObjectNode() : json.readTree(value);
    }

    private JsonNode getJson(String path, String token, int expectedStatus) throws Exception {
        String value = mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return value.isBlank() ? json.createObjectNode() : json.readTree(value);
    }

    private void deleteJson(String path, String token, int expectedStatus) throws Exception {
        mvc.perform(delete(path).header("Authorization", bearer(token))).andExpect(status().is(expectedStatus));
    }

    private String bearer(String token) { return "Bearer " + token; }
}
