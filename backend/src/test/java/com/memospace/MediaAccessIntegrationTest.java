package com.memospace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaAccessIntegrationTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void realMediaIsReturnedForEveryVisibilityAndDeniedToUnauthorizedUsers() throws Exception {
        JsonNode owner = postJson("/api/auth/register",
                Map.of("username", "media_owner_test", "password", "Memo123!", "nickname", "媒体所有者"), null, 200);
        JsonNode partner = postJson("/api/auth/register",
                Map.of("username", "media_partner_test", "password", "Memo123!", "nickname", "关系成员"), null, 200);
        JsonNode intruder = postJson("/api/auth/register",
                Map.of("username", "media_intruder", "password", "Memo123!", "nickname", "越权用户"), null, 200);
        String ownerToken = owner.get("token").asText();
        String partnerToken = partner.get("token").asText();
        String intruderToken = intruder.get("token").asText();

        JsonNode invitation = postJson("/api/relationships/invitations",
                Map.of("receiverId", partner.at("/user/id").asLong(), "relationshipType", "FRIEND", "message", "媒体权限测试"), ownerToken, 200);
        JsonNode accepted = postJson("/api/relationships/invitations/" + invitation.get("id").asLong() + "/accept",
                Map.of(), partnerToken, 200);
        long relationshipSpaceId = accepted.get("spaceId").asLong();

        long publicFile = upload(ownerToken, "public.png");
        long privateFile = upload(ownerToken, "private.png");
        long relationshipFile = upload(ownerToken, "relationship.png");

        JsonNode publicMemory = createMemory(ownerToken, "PUBLIC 媒体", "PUBLIC", publicFile, List.of());
        JsonNode privateMemory = createMemory(ownerToken, "PRIVATE 媒体", "PRIVATE", privateFile, List.of());
        JsonNode relationshipMemory = createMemory(ownerToken, "RELATIONSHIP 媒体", "RELATIONSHIP", relationshipFile,
                List.of(relationshipSpaceId));

        assertEquals(publicFile, publicMemory.get("cover_file_id").asLong());
        assertEquals(privateFile, privateMemory.get("cover_file_id").asLong());
        assertEquals(relationshipFile, relationshipMemory.get("cover_file_id").asLong());
        assertEquals(relationshipFile, relationshipMemory.at("/media/0/file_id").asLong());

        expectPng(ownerToken, publicFile);
        expectPng(ownerToken, privateFile);
        expectPng(ownerToken, relationshipFile);
        expectPng(partnerToken, publicFile);
        expectPng(partnerToken, relationshipFile);
        expectForbidden(partnerToken, privateFile);
        expectPng(intruderToken, publicFile);
        expectForbidden(intruderToken, privateFile);
        expectForbidden(intruderToken, relationshipFile);

        JsonNode mine = getJson("/api/memories", ownerToken, 200);
        assertTrue(hasCover(mine, publicMemory.get("id").asLong(), publicFile));
        assertTrue(hasCover(mine, privateMemory.get("id").asLong(), privateFile));
        JsonNode timeline = getJson("/api/spaces/" + relationshipSpaceId + "/timeline", partnerToken, 200);
        assertTrue(hasCover(timeline, relationshipMemory.get("id").asLong(), relationshipFile));
    }

    private JsonNode createMemory(String token, String title, String visibility, long fileId, List<Long> spaceIds) throws Exception {
        return postJson("/api/memories", Map.of(
                "title", title,
                "content", "返回真实图片字节",
                "memoryType", "PHOTO",
                "visibility", visibility,
                "spaceIds", spaceIds,
                "fileIds", List.of(fileId)), token, 200);
    }

    private long upload(String token, String filename) throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", filename, "application/octet-stream", PNG);
        String result = mvc.perform(multipart("/api/files").file(upload).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    private void expectPng(String token, long fileId) throws Exception {
        mvc.perform(get("/api/files/{id}/content", fileId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
    }

    private void expectForbidden(String token, long fileId) throws Exception {
        mvc.perform(get("/api/files/{id}/content", fileId).header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    private boolean hasCover(JsonNode items, long memoryId, long fileId) {
        for (JsonNode item : items) {
            if (item.get("id").asLong() == memoryId && item.get("cover_file_id").asLong() == fileId) return true;
        }
        return false;
    }

    private JsonNode getJson(String path, String token, int expectedStatus) throws Exception {
        String result = mvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return json.readTree(result);
    }

    private JsonNode postJson(String path, Object body, String token, int expectedStatus) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        String result = mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return result.isBlank() ? json.createObjectNode() : json.readTree(result);
    }

    private String bearer(String token) { return "Bearer " + token; }
}
