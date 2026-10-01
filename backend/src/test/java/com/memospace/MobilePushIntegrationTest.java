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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobilePushIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void deviceRegistrationWorksEvenWhenFirebaseProviderIsNotConfigured() throws Exception {
        JsonNode login = post("/api/auth/register", Map.of(
                "username", "push_device_test", "password", "Memo123!", "nickname", "推送设备"), null);
        String token = login.get("token").asText();
        String deviceToken = "test-fcm-token-that-is-long-enough-for-validation-123456789";

        JsonNode registered = post("/api/push/devices", Map.of(
                "token", deviceToken, "platform", "ANDROID", "deviceName", "Redmi K70"), token);
        assertEquals(1, registered.get("registeredDevices").asInt());
        assertFalse(registered.get("providerConfigured").asBoolean());

        mvc.perform(delete("/api/push/devices").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("token", deviceToken))))
                .andExpect(status().isOk());
        String statusJson = mvc.perform(get("/api/push/status").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(0, json.readTree(statusJson).get("registeredDevices").asInt());
    }

    private JsonNode post(String path, Object body, String token) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) request.header("Authorization", bearer(token));
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String bearer(String token) { return "Bearer " + token; }
}
