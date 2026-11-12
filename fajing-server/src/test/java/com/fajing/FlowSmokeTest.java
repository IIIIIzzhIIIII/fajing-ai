package com.fajing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.service.AiImageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 主流程冒烟：登录 → 生成 → 轮询成功 → 记录存在 → 第 1 款默认解锁 → 下单 → 解锁增加。
 * 需要本机 MySQL（库 fajing_test，可用 TEST_DB_URL 覆盖），AI 出图用 Mock 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlowSmokeTest {

    private static final String AI_IMAGE = "http://localhost:8081/uploads/ai_smoke_test.png";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiImageService aiImageService;

    @Test
    void loginGeneratePayFlow() throws Exception {
        when(aiImageService.editHair(any(), any(), any(), any(), anyString())).thenReturn(AI_IMAGE);

        // 1. 演示登录拿 token
        String token = login();

        // 2. 创建生成任务（两款发型）
        MvcResult created = mvc.perform(post("/api/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"photoUrl\":\"http://localhost:8081/uploads/before.jpg\","
                                + "\"gender\":\"female\",\"mode\":\"hair\",\"styleIds\":[\"f1\",\"f2\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        long taskId = body(created).path("data").path("taskId").asLong();
        assertThat(taskId).isPositive();

        // 3. 轮询到 success
        JsonNode task = null;
        for (int i = 0; i < 80; i++) {
            MvcResult result = mvc.perform(get("/api/generate/" + taskId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            task = body(result).path("data");
            if ("success".equals(task.path("status").asText())) {
                break;
            }
            Thread.sleep(500);
        }
        assertThat(task).isNotNull();
        assertThat(task.path("status").asText()).isEqualTo("success");
        assertThat(task.path("result").path("styles")).hasSize(2);

        // 4. 试发记录已落库
        JsonNode records = body(mvc.perform(get("/api/records")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data");
        boolean recordExists = false;
        for (JsonNode record : records) {
            if (record.path("taskId").asLong() == taskId) {
                recordExists = true;
                break;
            }
        }
        assertThat(recordExists).isTrue();

        // 5. 第 1 款默认解锁
        JsonNode unlocksBefore = body(mvc.perform(get("/api/unlocks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(hasUnlock(unlocksBefore, taskId, "f1")).isTrue();
        int beforeCount = unlocksBefore.size();

        // 6. 下单解锁第 2 款（未接微信支付，走 mock 直接支付成功）
        MvcResult paid = mvc.perform(post("/api/order/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"styleIds\":[\"f2\"],\"plan\":\"one\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(paid).path("data").path("paid").asBoolean()).isTrue();

        // 7. 解锁列表增加且包含第 2 款
        JsonNode unlocksAfter = body(mvc.perform(get("/api/unlocks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(unlocksAfter.size()).isEqualTo(beforeCount + 1);
        assertThat(hasUnlock(unlocksAfter, taskId, "f2")).isTrue();
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = body(result).path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private boolean hasUnlock(JsonNode unlocks, long taskId, String styleId) {
        for (JsonNode unlock : unlocks) {
            if (unlock.path("taskId").asLong() == taskId && styleId.equals(unlock.path("styleId").asText())) {
                return true;
            }
        }
        return false;
    }
}
