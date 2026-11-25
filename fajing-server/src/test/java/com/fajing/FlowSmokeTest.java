package com.fajing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.entity.AppUser;
import com.fajing.entity.UserQuota;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.UserQuotaMapper;
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

    @Autowired
    private AppUserMapper userMapper;

    @Autowired
    private UserQuotaMapper quotaMapper;

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

        // 6. 充值魅力值（未接微信支付，走 mock 直接支付成功）
        long pointsBefore = body(mvc.perform(get("/api/quota")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data").path("points").asLong();
        MvcResult paid = mvc.perform(post("/api/order/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bizType\":\"points\",\"packageId\":1,\"platform\":\"h5\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(body(paid).path("data").path("paid").asBoolean()).isTrue();

        // 7. 魅力值到账，解锁记录保持
        JsonNode quotaAfter = body(mvc.perform(get("/api/quota")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(quotaAfter.path("points").asLong()).isEqualTo(pointsBefore + 100);
        JsonNode unlocksAfter = body(mvc.perform(get("/api/unlocks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).path("data");
        assertThat(unlocksAfter.size()).isGreaterThanOrEqualTo(beforeCount);
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = body(result).path("data").path("token").asText();
        assertThat(token).isNotBlank();
        // 支付前必须绑定手机号：测试用户直接补一个手机号，避免依赖微信授权
        AppUser user = userMapper.selectById(1L);
        if (user != null && (user.getPhone() == null || user.getPhone().isBlank())) {
            user.setPhone("13800000000");
            userMapper.updateById(user);
        }
        // 用例多次运行会耗尽终身免费批次，这里重置为 3 批，保证「首款默认解锁」断言稳定
        UserQuota quota = quotaMapper.selectById(1L);
        if (quota == null) {
            quota = new UserQuota();
            quota.setUserId(1L);
            quota.setPoints(50);
            quota.setFreeBatches(3);
            quota.setFreeLeft(3);
            quota.setQuotaDate(java.time.LocalDate.now());
            quota.setUpdatedAt(java.time.LocalDateTime.now());
            quotaMapper.insert(quota);
        } else {
            quota.setFreeBatches(3);
            quota.setFreeLeft(3);
            quotaMapper.updateById(quota);
        }
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
