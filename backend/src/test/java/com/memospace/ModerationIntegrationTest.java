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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.admin.username=moderation_admin_test",
        "app.admin.password=ModerationAdmin2026!",
        "app.admin.nickname=审核管理员"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModerationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void reportReviewMuteRestoreAndBanJourney() throws Exception {
        JsonNode author = register("moderation_author", "被举报用户");
        JsonNode reporter = register("moderation_reporter", "举报用户");
        String authorToken = author.get("token").asText();
        String reporterToken = reporter.get("token").asText();
        long authorId = author.at("/user/id").asLong();

        long evidenceFileId = uploadImage(authorToken, "evidence.png");
        long unrelatedFileId = uploadImage(authorToken, "unrelated.png");
        JsonNode memory = postJson("/api/memories", memoryRequest("需要审核的公开内容", "PUBLIC", List.of(evidenceFileId)), authorToken, 200);
        postJson("/api/memories", memoryRequest("不属于举报的私人内容", "PRIVATE", List.of(unrelatedFileId)), authorToken, 200);
        long memoryId = memory.get("id").asLong();
        postJson("/api/reports", Map.of("targetType", "MEMORY", "targetId", memoryId,
                "reasonCategory", "ILLEGAL", "description", "请管理员核查"), authorToken, 400);
        JsonNode report = postJson("/api/reports", Map.of("targetType", "MEMORY", "targetId", memoryId,
                "reasonCategory", "ILLEGAL", "description", "请管理员核查"), reporterToken, 200);
        postJson("/api/reports", Map.of("targetType", "MEMORY", "targetId", memoryId,
                "reasonCategory", "ILLEGAL"), reporterToken, 409);

        String adminToken = adminLogin();
        JsonNode inbox = getJson("/api/admin/reports?status=PENDING", adminToken, 200);
        assertTrue(inbox.get("items").toString().contains("moderation_author"));
        JsonNode detail = getJson("/api/admin/reports/" + report.get("id").asLong(), adminToken, 200);
        assertEquals("需要审核的公开内容", detail.at("/target/title").asText());
        assertEquals(evidenceFileId, detail.at("/media/0/file_id").asLong());
        mvc.perform(get("/api/admin/reports/{reportId}/media/{fileId}", report.get("id").asLong(), evidenceFileId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/{reportId}/media/{fileId}", report.get("id").asLong(), unrelatedFileId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());

        putJson("/api/admin/reports/" + report.get("id").asLong() + "/resolve", Map.of(
                "decision", "CONFIRM", "removeContent", false, "penalty", "MUTE_7_DAYS", "note", "首次确认违规"), adminToken, 200);
        getJson("/api/memories/" + memoryId, authorToken, 200);
        postJson("/api/memories", memoryRequest("禁言时不能发布"), authorToken, 403);
        postJson("/api/memories/" + memoryId + "/comments", Map.of("content", "禁言时不能评论"), authorToken, 403);

        JsonNode user = getJson("/api/admin/users?keyword=moderation_author", adminToken, 200).at("/items/0");
        assertEquals(1, user.get("violation_count").asInt());
        assertTrue(getJson("/api/notifications", authorToken, 200).toString().contains("禁言 7 天"));

        putJson("/api/admin/users/" + authorId + "/status", Map.of("action", "ACTIVE", "note", "复核后解除"), adminToken, 200);
        createPublicMemory(authorToken, "解除后可以发布");
        putJson("/api/admin/users/" + authorId + "/status", Map.of("action", "BAN", "note", "严重违规"), adminToken, 200);
        getJson("/api/home", authorToken, 403);
        postJson("/api/auth/login", Map.of("username", "moderation_author", "password", "Memo123!"), null, 403);

        putJson("/api/admin/users/" + authorId + "/status", Map.of("action", "ACTIVE", "note", "申诉通过"), adminToken, 200);
        postJson("/api/auth/login", Map.of("username", "moderation_author", "password", "Memo123!"), null, 200);
    }

    private JsonNode createPublicMemory(String token, String title) throws Exception {
        return postJson("/api/memories", memoryRequest(title), token, 200);
    }

    private Map<String, Object> memoryRequest(String title) {
        return memoryRequest(title, "PUBLIC", List.of());
    }

    private Map<String, Object> memoryRequest(String title, String visibility, List<Long> fileIds) {
        return Map.of("title", title, "content", "测试审核证据", "memoryType", fileIds.isEmpty() ? "TEXT" : "PHOTO",
                "occurredAt", LocalDateTime.now().withNano(0).toString(), "visibility", visibility,
                "spaceIds", List.of(), "customViewerIds", List.of(), "fileIds", fileIds);
    }

    private long uploadImage(String token, String filename) throws Exception {
        byte[] tinyPng = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile upload = new MockMultipartFile("file", filename, "image/png", tinyPng);
        String result = mvc.perform(multipart("/api/files").file(upload).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    private JsonNode register(String username, String nickname) throws Exception {
        return postJson("/api/auth/register", Map.of("username", username, "password", "Memo123!", "nickname", nickname), null, 200);
    }

    private String adminLogin() throws Exception {
        return postJson("/api/admin/auth/login", Map.of("username", "moderation_admin_test", "password", "ModerationAdmin2026!"), null, 200).get("token").asText();
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
