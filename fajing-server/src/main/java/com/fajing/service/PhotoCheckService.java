package com.fajing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.entity.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传前照片审查：用视觉模型判断是否为「单人、真实正脸、脸部清晰无遮挡」的照片。
 * 只做放行/拦截判断，失败时放行（不影响正常用户流程），并记录日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoCheckService {

    private final AiConfigService configService;
    private final AiImageService imageService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /** 视觉模型：默认硅基流动 Qwen3-VL，可通过 AI_VISION_MODEL 覆盖 */
    @Value("${ai.vision-model:Qwen/Qwen3-VL-8B-Instruct}")
    private String visionModel;

    @Value("${ai.photo-check-enabled:true}")
    private boolean enabled;

    private static final String PROMPT = "判断这张照片是否适合用于「AI 换发型」：必须是真实或写实风格的单人照片，"
            + "人物正脸朝向镜头、脸部完整清晰、无遮挡（不能戴墨镜/口罩、不能用手或头发挡住五官），"
            + "不是侧脸、背影、多人合照、风景、卡通插画或截图。\n"
            + "只输出以下格式：第一行 YES 或 NO，第二行用一句中文说明原因。";

    public Map<String, Object> check(String photoUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!enabled) {
            result.put("ok", true);
            result.put("skipped", true);
            return result;
        }
        AiConfig cfg = configService.get();
        if (cfg.getImageApiKey() == null || cfg.getImageApiKey().isBlank()) {
            // 未配置视觉模型密钥时不做拦截
            result.put("ok", true);
            result.put("skipped", true);
            return result;
        }
        try {
            String dataUri = imageService.toDataUri(photoUrl);
            String content = callVision(cfg, dataUri);
            String head = content == null ? "" : content.trim().toUpperCase();
            boolean ok = head.startsWith("YES") || head.startsWith("是");
            result.put("ok", ok);
            result.put("reason", ok ? "" : summarize(content));
            result.put("model", visionModel);
            return result;
        } catch (Exception e) {
            log.warn("照片自动审查失败，放行本次上传：{}", e.getMessage());
            result.put("ok", true);
            result.put("skipped", true);
            return result;
        }
    }

    private String callVision(AiConfig cfg, String dataUri) throws Exception {
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        imagePart.put("image_url", Map.of("url", dataUri));

        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", PROMPT));
        content.add(imagePart);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0);
        body.put("max_tokens", 80);

        String endpoint = trimSlash(cfg.getImageBaseUrl()) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + cfg.getImageApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("视觉模型返回 " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText("");
    }

    /** 截断成一句可读原因，避免把整段模型输出塞进 toast */
    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "请上传正面、清晰的本人照片";
        }
        String text = content.replace("\r", "").trim();
        int lineBreak = text.indexOf('\n');
        if (lineBreak > -1) {
            text = text.substring(lineBreak + 1).trim();
        }
        text = text.replaceFirst("^(NO|否)[:：,，\\s]*", "");
        return text.length() > 40 ? text.substring(0, 40) : text;
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
