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
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoreJourneyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void completeRelationshipMemoryAndArchiveJourney() throws Exception {
        JsonNode alice = postJson("/api/auth/register", Map.of("username", "alice_test", "password", "Memo123!", "nickname", "Alice"), null, 200);
        JsonNode bob = postJson("/api/auth/register", Map.of("username", "bob_test", "password", "Memo123!", "nickname", "Bob"), null, 200);
        String aliceToken = alice.get("token").asText();
        String bobToken = bob.get("token").asText();
        long bobId = bob.at("/user/id").asLong();

        JsonNode invitation = postJson("/api/relationships/invitations",
                Map.of("receiverId", bobId, "relationshipType", "FRIEND", "message", "一起记录吧"), aliceToken, 200);
        JsonNode accepted = postJson("/api/relationships/invitations/" + invitation.get("id").asLong() + "/accept", Map.of(), bobToken, 200);
        long spaceId = accepted.get("spaceId").asLong();
        long relationshipId = accepted.get("relationshipId").asLong();

        byte[] tinyPng = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile upload = new MockMultipartFile("file", "memory.png", "application/octet-stream", tinyPng);
        String uploadResult = mvc.perform(multipart("/api/files").file(upload).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long fileId = json.readTree(uploadResult).get("id").asLong();

        JsonNode memory = postJson("/api/memories", Map.of(
                "title", "第一段共同记忆", "content", "真实联调路径", "memoryType", "PHOTO",
                "occurredAt", LocalDateTime.now().withNano(0).toString(), "visibility", "RELATIONSHIP", "spaceIds", List.of(spaceId), "fileIds", List.of(fileId)), aliceToken, 200);
        long memoryId = memory.get("id").asLong();

        mvc.perform(get("/api/memories/{id}", memoryId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/files/{id}/content", fileId).header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk());
        postJson("/api/memories/" + memoryId + "/comments", Map.of("content", "我也记得这一天"), bobToken, 200);
        mvc.perform(put("/api/memories/{id}", memoryId).header("Authorization", bearer(aliceToken))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("visibility", "PUBLIC"))))
                .andExpect(status().isOk());
        String feed = mvc.perform(get("/api/feed").header("Authorization", bearer(bobToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(feed.contains("第一段共同记忆"));

        mvc.perform(delete("/api/relationships/{id}", relationshipId).header("Authorization", bearer(aliceToken)))
                .andExpect(status().isOk());
        postJson("/api/memories", Map.of("title", "不应成功", "memoryType", "TEXT", "visibility", "RELATIONSHIP", "spaceIds", List.of(spaceId)), aliceToken, 403);
    }

    @Test
    void privateMemoryCannotBeReadByAnotherUser() throws Exception {
        JsonNode demo = postJson("/api/auth/login", Map.of("username", "demo", "password", "Memo123!"), null, 200);
        JsonNode mia = postJson("/api/auth/login", Map.of("username", "mia", "password", "Memo123!"), null, 200);
        String mine = mvc.perform(get("/api/memories").header("Authorization", bearer(demo.get("token").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = json.readTree(mine);
        long privateId = 0;
        for (JsonNode item : list) if ("PRIVATE".equals(item.get("visibility").asText())) privateId = item.get("id").asLong();
        assertTrue(privateId > 0);
        mvc.perform(get("/api/memories/{id}", privateId).header("Authorization", bearer(mia.get("token").asText())))
                .andExpect(status().isForbidden());
    }

    @Test
    void relationshipCategoriesHideRestoreAndReuseOneSpace() throws Exception {
        JsonNode carol = postJson("/api/auth/register", Map.of(
                "username", "carol_categories", "password", "Memo123!", "nickname", "Carol"), null, 200);
        JsonNode dave = postJson("/api/auth/register", Map.of(
                "username", "dave_categories", "password", "Memo123!", "nickname", "Dave"), null, 200);
        String carolToken = carol.get("token").asText();
        String daveToken = dave.get("token").asText();
        long daveId = dave.at("/user/id").asLong();

        JsonNode initialCategories = getJson("/api/relationship-categories?includeHidden=true", carolToken, 200);
        assertEquals(4, initialCategories.size());
        long buddyId = categoryId(initialCategories, "BUDDY");
        long bestieId = categoryId(initialCategories, "BESTIE");
        long familyId = categoryId(initialCategories, "FAMILY");

        JsonNode invitation = postJson("/api/relationships/invitations",
                Map.of("receiverId", daveId, "categoryId", buddyId, "message", "归到死党分类"), carolToken, 200);
        JsonNode accepted = postJson("/api/relationships/invitations/" + invitation.get("id").asLong() + "/accept",
                Map.of(), daveToken, 200);
        long relationshipId = accepted.get("relationshipId").asLong();
        long spaceId = accepted.get("spaceId").asLong();

        putJson("/api/relationships/" + relationshipId + "/categories",
                Map.of("categoryIds", List.of(buddyId, bestieId)), carolToken, 200);
        JsonNode bestie = getJson("/api/relationship-categories/" + bestieId, carolToken, 200);
        assertEquals(1, bestie.get("people").size());
        assertEquals(spaceId, bestie.at("/people/0/space_id").asLong());

        putJson("/api/relationship-categories/" + bestieId + "/visibility", Map.of("visible", false), carolToken, 200);
        JsonNode visibleCategories = getJson("/api/relationship-categories", carolToken, 200);
        assertFalse(containsCategory(visibleCategories, bestieId));
        JsonNode hiddenDetail = getJson("/api/relationship-categories/" + bestieId, carolToken, 200);
        assertEquals(spaceId, hiddenDetail.at("/people/0/space_id").asLong());
        putJson("/api/relationship-categories/" + bestieId + "/visibility", Map.of("visible", true), carolToken, 200);
        assertTrue(containsCategory(getJson("/api/relationship-categories", carolToken, 200), bestieId));

        JsonNode secondInvitation = postJson("/api/relationships/invitations",
                Map.of("receiverId", daveId, "categoryId", familyId, "message", "同一关系增加家人标签"), carolToken, 200);
        JsonNode reused = postJson("/api/relationships/invitations/" + secondInvitation.get("id").asLong() + "/accept",
                Map.of(), daveToken, 200);
        assertTrue(reused.get("reusedSpace").asBoolean());
        assertEquals(relationshipId, reused.get("relationshipId").asLong());
        assertEquals(spaceId, reused.get("spaceId").asLong());
        assertEquals(1, getJson("/api/relationships", carolToken, 200).size());
    }

    @Test
    void relationshipInvitationCanBeRejectedWithoutCreatingRelationshipOrSpace() throws Exception {
        JsonNode sender = postJson("/api/auth/register", Map.of(
                "username", "reject_sender", "password", "Memo123!", "nickname", "邀请方"), null, 200);
        JsonNode receiver = postJson("/api/auth/register", Map.of(
                "username", "reject_receiver", "password", "Memo123!", "nickname", "接收方"), null, 200);
        String senderToken = sender.get("token").asText();
        String receiverToken = receiver.get("token").asText();
        long categoryId = categoryId(getJson("/api/relationship-categories", senderToken, 200), "FAMILY");

        JsonNode invitation = postJson("/api/relationships/invitations", Map.of(
                "receiverId", receiver.at("/user/id").asLong(), "categoryId", categoryId, "message", "是否建立家人关系"),
                senderToken, 200);
        JsonNode rejected = postJson("/api/relationships/invitations/" + invitation.get("id").asLong() + "/reject",
                Map.of(), receiverToken, 200);

        assertEquals("REJECTED", rejected.get("status").asText());
        assertEquals(0, getJson("/api/relationships", senderToken, 200).size());
        JsonNode invitationHistory = getJson("/api/relationships/invitations", senderToken, 200);
        assertEquals("REJECTED", invitationHistory.get(0).get("status").asText());
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
        var request = get(path);
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private long categoryId(JsonNode categories, String key) {
        for (JsonNode category : categories) {
            if (key.equals(category.get("category_key").asText())) return category.get("id").asLong();
        }
        throw new AssertionError("Missing category: " + key);
    }

    private boolean containsCategory(JsonNode categories, long categoryId) {
        for (JsonNode category : categories) if (category.get("id").asLong() == categoryId) return true;
        return false;
    }

    private String bearer(String token) { return "Bearer " + token; }
}
