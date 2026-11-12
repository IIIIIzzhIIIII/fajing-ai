package com.fajing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.entity.AiConfig;
import com.fajing.entity.Hairstyle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提示词服务：先用后台配置的模板拼出编辑指令；
 * 开启增强时再让文案模型（默认 DeepSeek）改写成更专业的指令，失败自动回退模板。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptService {

    private final AiConfigService configService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String build(Hairstyle style, String colorName) {
        AiConfig cfg = configService.get();
        String merged = fill(cfg.getPromptTemplate(), style, colorName);
        boolean enhance = cfg.getUseTextEnhance() != null && cfg.getUseTextEnhance() == 1
                && cfg.getTextApiKey() != null && !cfg.getTextApiKey().isBlank();
        if (!enhance) {
            return merged;
        }
        try {
            String improved = enhance(cfg, style, colorName, merged);
            return improved == null || improved.isBlank() ? merged : improved;
        } catch (Exception e) {
            log.warn("文案模型改写提示词失败，回退模板: {}", e.getMessage());
            return merged;
        }
    }

    private String fill(String template, Hairstyle style, String colorName) {
        String tpl = template == null || template.isBlank()
                ? AiConfigService.DEFAULT_PROMPT
                : template;
        return tpl
                .replace("{style}", nvl(style.getName()))
                .replace("{en}", nvl(style.getEn()))
                .replace("{len}", nvl(style.getLen()))
                .replace("{face}", nvl(style.getFace()))
                .replace("{color}", colorName == null || colorName.isBlank() ? "" : "把发色调整为" + colorName + "，")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String enhance(AiConfig cfg, Hairstyle style, String colorName, String basePrompt) throws Exception {
        String userContent = "目标发型：" + nvl(style.getName())
                + "\n英文造型描述：" + nvl(style.getEn())
                + "\n长度：" + nvl(style.getLen())
                + "\n适合脸型：" + nvl(style.getFace())
                + (colorName == null || colorName.isBlank() ? "" : "\n目标发色：" + colorName)
                + "\n基础编辑指令：" + basePrompt;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getTextModel());
        body.put("temperature", 0.6);
        body.put("max_tokens", 400);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", nvl(cfg.getSystemPrompt(), AiConfigService.DEFAULT_SYSTEM_PROMPT)),
                Map.of("role", "user", "content", userContent)
        ));

        HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(cfg.getTextBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + cfg.getTextApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + shorten(resp.body()));
        }
        JsonNode root = objectMapper.readTree(resp.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        content = content.trim().replaceAll("^[\"“”']+|[\"“”']+$", "").trim();
        return content.length() > 1200 ? content.substring(0, 1200) : content;
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nvl(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
