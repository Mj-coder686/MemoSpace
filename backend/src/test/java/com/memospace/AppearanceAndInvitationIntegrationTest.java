package com.memospace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppearanceAndInvitationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void friendThenRelationshipInvitationIsVisibleToReceiver() throws Exception {
        JsonNode alice = register("invite_flow_alice", "申请者");
        JsonNode bob = register("invite_flow_bob", "接收者");
        String aliceToken = alice.get("token").asText();
        String bobToken = bob.get("token").asText();
        long bobId = bob.at("/user/id").asLong();

        JsonNode friendRequest = postJson("/api/friends/requests", Map.of("receiverId", bobId, "message", "先成为好友"), aliceToken, 200);
        assertTrue(getJson("/api/notifications", bobToken, 200).toString().contains("收到好友申请"));
        postJson("/api/friends/requests/" + friendRequest.get("id").asLong() + "/accept", Map.of(), bobToken, 200);

        JsonNode categories = getJson("/api/relationship-categories", aliceToken, 200);
        JsonNode relationInvite = postJson("/api/relationships/invitations", Map.of(
                "receiverId", bobId, "categoryId", categories.get(0).get("id").asLong(), "message", "建立共同空间"), aliceToken, 200);

        JsonNode received = getJson("/api/relationships/invitations", bobToken, 200);
        assertEquals(relationInvite.get("id").asLong(), received.get(0).get("id").asLong());
        assertEquals("PENDING", received.get(0).get("status").asText());
        assertTrue(getJson("/api/notifications", bobToken, 200).toString().contains("收到关系邀请"));
    }

    @Test
    void avatarAndBackgroundFilesKeepTheirIntendedPermissions() throws Exception {
        JsonNode alice = register("appearance_alice", "自定义甲");
        JsonNode bob = register("appearance_bob", "自定义乙");
        JsonNode outsider = register("appearance_outsider", "旁观者");
        String aliceToken = alice.get("token").asText();
        String bobToken = bob.get("token").asText();
        String outsiderToken = outsider.get("token").asText();
        long backgroundId = uploadImage(aliceToken, "background.png");

        putJson("/api/users/me/avatar", Map.of("fileId", backgroundId), aliceToken, 200);
        mvc.perform(get("/api/files/{id}/content", backgroundId).header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isOk());
        putJson("/api/users/me/avatar", Map.of("fileId", backgroundId), outsiderToken, 400);

        long privateBackgroundId = uploadImage(aliceToken, "private-background.png");
        JsonNode appearance = putJson("/api/users/me/appearance", Map.of(
                "backgroundColor", "#6a5d66", "backgroundFileId", privateBackgroundId,
                "backgroundBrightness", 74, "backgroundOverlay", 22, "clearBackgroundImage", false), aliceToken, 200);
        assertEquals(74, appearance.get("background_brightness").asInt());
        mvc.perform(get("/api/files/{id}/content", privateBackgroundId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isForbidden());

        JsonNode category = getJson("/api/relationship-categories", aliceToken, 200).get(0);
        JsonNode invite = postJson("/api/relationships/invitations", Map.of(
                "receiverId", bob.at("/user/id").asLong(), "categoryId", category.get("id").asLong()), aliceToken, 200);
        JsonNode accepted = postJson("/api/relationships/invitations/" + invite.get("id").asLong() + "/accept", Map.of(), bobToken, 200);
        long spaceId = accepted.get("spaceId").asLong();
        JsonNode space = putJson("/api/spaces/" + spaceId + "/appearance", Map.of(
                "name", "我们的自定义空间", "primaryColor", "#765b70", "backgroundColor", "#e7dce2",
                "textColor", "#fffaf5", "backgroundFileId", privateBackgroundId,
                "backgroundBrightness", 68, "backgroundOverlay", 28, "clearBackgroundImage", false), aliceToken, 200);
        assertEquals("我们的自定义空间", space.get("name").asText());
        assertEquals("#765b70", space.get("primary_color").asText());
        mvc.perform(get("/api/files/{id}/content", privateBackgroundId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}/content", privateBackgroundId).header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isForbidden());
        putJson("/api/spaces/" + spaceId + "/appearance", Map.of(
                "primaryColor", "#111111", "backgroundColor", "#222222", "textColor", "#ffffff",
                "backgroundBrightness", 80, "backgroundOverlay", 10, "clearBackgroundImage", false), outsiderToken, 403);
    }

    private long uploadImage(String token, String name) throws Exception {
        byte[] tinyPng = new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,0,0,0,0};
        MockMultipartFile upload = new MockMultipartFile("file", name, "image/png", tinyPng);
        String result = mvc.perform(multipart("/api/files").file(upload).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    private JsonNode register(String username, String nickname) throws Exception {
        return postJson("/api/auth/register", Map.of("username", username, "password", "Memo123!", "nickname", nickname), null, 200);
    }

    private JsonNode postJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private JsonNode putJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = put(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private JsonNode getJson(String path, String token, int expectedStatus) throws Exception {
        String result = mvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private String bearer(String token) { return "Bearer " + token; }
}
