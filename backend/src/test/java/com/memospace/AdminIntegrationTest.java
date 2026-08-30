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
        "app.admin.username=system_admin_test",
        "app.admin.password=AdminTest2026!",
        "app.admin.nickname=测试管理员"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void normalUsersCannotEnterAdministratorApiAndAdminCannotUseNormalLogin() throws Exception {
        JsonNode normal = register("admin_boundary_user", "普通用户");
        String normalToken = normal.get("token").asText();

        postJson("/api/admin/auth/login", Map.of("username", "admin_boundary_user", "password", "Memo123!"), null, 403);
        getJson("/api/admin/users", normalToken, 403);
        postJson("/api/auth/login", Map.of("username", "system_admin_test", "password", "AdminTest2026!"), null, 403);

        String adminToken = adminLogin();
        long adminId = getJson("/api/admin/me", adminToken, 200).get("id").asLong();
        assertEquals(0, getJson("/api/users/search?q=system_admin_test", normalToken, 200).size());
        getJson("/api/users/" + adminId, normalToken, 404);
        postJson("/api/users/" + adminId + "/follow", Map.of(), normalToken, 404);
    }

    @Test
    void administratorCanResetPasswordAndChangeUniqueNumericMemoId() throws Exception {
        JsonNode target = register("admin_action_target", "被协助用户");
        JsonNode other = register("admin_action_other", "另一位用户");
        long targetId = target.at("/user/id").asLong();
        long otherId = other.at("/user/id").asLong();
        String adminToken = adminLogin();

        JsonNode users = getJson("/api/admin/users?keyword=admin_action_target", adminToken, 200);
        assertEquals(targetId, users.at("/items/0/id").asLong());

        JsonNode updated = putJson("/api/admin/users/" + targetId + "/memo-id",
                Map.of("memoId", "100000000001"), adminToken, 200);
        assertEquals("100000000001", updated.get("public_id").asText());
        putJson("/api/admin/users/" + otherId + "/memo-id",
                Map.of("memoId", "100000000001"), adminToken, 409);
        putJson("/api/admin/users/" + targetId + "/memo-id",
                Map.of("memoId", "ABC123"), adminToken, 400);

        putJson("/api/admin/users/" + targetId + "/password",
                Map.of("newPassword", "Temporary2026!"), adminToken, 200);
        postJson("/api/auth/login", Map.of("username", "admin_action_target", "password", "Memo123!"), null, 401);
        postJson("/api/auth/login", Map.of("username", "admin_action_target", "password", "Temporary2026!"), null, 200);
        JsonNode audit = getJson("/api/admin/audit", adminToken, 200);
        assertTrue(audit.toString().contains("RESET_PASSWORD"));
        assertTrue(audit.toString().contains("CHANGE_MEMO_ID"));
    }

    @Test
    void administratorSessionCannotReadPrivateMemoryUploadedImageOrUserFeatures() throws Exception {
        JsonNode owner = register("admin_privacy_owner", "隐私内容拥有者");
        String ownerToken = owner.get("token").asText();
        long fileId = uploadImage(ownerToken);
        JsonNode memory = postJson("/api/memories", Map.of(
                "title", "管理员不可见的私人记忆",
                "content", "这段文字只能由用户权限系统处理。",
                "memoryType", "PHOTO",
                "occurredAt", LocalDateTime.now().toString(),
                "visibility", "PRIVATE",
                "spaceIds", List.of(),
                "customViewerIds", List.of(),
                "fileIds", List.of(fileId)), ownerToken, 200);
        String adminToken = adminLogin();

        getJson("/api/memories/" + memory.get("id").asLong(), adminToken, 403);
        mvc.perform(get("/api/files/{id}/content", fileId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden());
        getJson("/api/spaces", adminToken, 403);
        getJson("/api/chats/1/messages", adminToken, 403);
    }

    private String adminLogin() throws Exception {
        return postJson("/api/admin/auth/login", Map.of(
                "username", "system_admin_test", "password", "AdminTest2026!"), null, 200).get("token").asText();
    }

    private long uploadImage(String token) throws Exception {
        byte[] tinyPng = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile upload = new MockMultipartFile("file", "private.png", "image/png", tinyPng);
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
