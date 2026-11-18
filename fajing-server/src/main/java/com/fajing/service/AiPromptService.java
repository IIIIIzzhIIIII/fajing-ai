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
        return build(style, colorName, "");
    }

    /**
     * @param colorEn 发色英文描述（如 chestnut brown），有值时按「染发模式」改写提示词，
     *                避免模板里的「保持黑色」与染发诉求互相冲突
     */
    public String build(Hairstyle style, String colorName, String colorEn) {
        AiConfig cfg = configService.get();
        String merged = fill(cfg.getPromptTemplate(), style, colorName);
        if (colorName != null && !colorName.isBlank()) {
            merged = applyColor(merged, colorName.trim(), colorEn == null ? "" : colorEn.trim());
        }
        boolean enhance = cfg.getUseTextEnhance() != null && cfg.getUseTextEnhance() == 1
                && cfg.getTextApiKey() != null && !cfg.getTextApiKey().isBlank()
                // 染发模式直接用模板：文案模型改写时容易丢掉发色约束
                && (colorName == null || colorName.isBlank());
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

    /** 染发模式：整段替换发色说明，并清掉「保持黑色」类冲突指令 */
    private String applyColor(String prompt, String colorName, String colorEn) {
        String en = colorEn.isBlank() ? colorName : colorEn;
        String colorBlock = "【发色 · 必须执行】把整头头发染成" + colorName + "（" + en + "），"
                + "发根到发尾颜色均匀一致、自然有光泽；只改变发色，不改变发型长度与形状。"
                + "不要保留原图黑发，不要出现黑色发根，也不要染成其他颜色。"
                + "Hair color must be " + en + " all over, evenly from roots to ends. "
                + "Do not keep the original black hair and do not use any other hair color.";
        String result = prompt
                .replaceAll("【发色】[^\\n]*", colorBlock)
                .replace("Hair must remain pure black like the original photo, no blue or grey tint.", "")
                .replace("Keep the original hair color (black).", "Change the hair color to " + en + ".")
                .replace("图2只参考发型轮廓、长度与层次，不要复制图2的发色。",
                        "图2只参考发型轮廓、长度与层次；本次发色以文字要求为准。");
        if (!result.contains("【发色 · 必须执行】")) {
            result = colorBlock + "\n" + result;
        }
        return result + "\n【发色验收】整头头发必须呈现" + colorName + "（" + en + "），颜色均匀，不要保留黑发根或杂色。";
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
