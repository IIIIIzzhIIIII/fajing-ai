package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.entity.AiConfig;
import com.fajing.entity.HairColor;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Recommendation;
import com.fajing.mapper.HairColorMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.RecommendationMapper;
import com.fajing.util.UserContext;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 人脸分析 + 发型/发色排名：
 * 1. 一次视觉模型调用拿到脸型与肤色（控制 Token）
 * 2. 排序与评分在服务端按标签规则完成，不再额外调用模型
 * 3. 结果落 recommendation 表，前端按排名展示，前 3 款由生成流程预出图
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    /** 每批免费款数 */
    public static final int FREE_TOP = 3;

    private final AiConfigService configService;
    private final AiImageService imageService;
    private final HairstyleMapper hairstyleMapper;
    private final HairColorMapper hairColorMapper;
    private final RecommendationMapper recommendationMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @Value("${ai.vision-model:Qwen/Qwen3-VL-8B-Instruct}")
    private String visionModel;

    @Value("${ai.vision-timeout-seconds:35}")
    private int visionTimeout;

    private static final String PROMPT = "分析这张人像照片的脸型与肤色，只输出一行 JSON，不要任何解释：\n"
            + "{\"faceShape\":\"鹅蛋脸|圆脸|方脸|长脸|心形脸|菱形脸\",\"skinTone\":\"冷白皮|暖黄皮|中性皮|小麦色\"}\n"
            + "脸型依据下颌线与颧骨宽度判断，肤色依据整体色调判断。";

    /** 分析照片并生成排名（不生成图片）；同一用户同一张照片 30 分钟内复用结果，避免重复消耗视觉模型 */
    public Map<String, Object> analyze(String photoUrl, String gender) {
        Long userId = UserContext.require();
        String g = "male".equals(gender) ? "male" : "female";
        Recommendation cached = recommendationMapper.selectOne(new QueryWrapper<Recommendation>()
                .eq("user_id", userId)
                .eq("photo_url", photoUrl)
                .eq("gender", g)
                .ge("created_at", LocalDateTime.now().minusMinutes(30))
                .orderByDesc("id")
                .last("limit 1"));
        if (cached != null) {
            return toData(cached);
        }
        String faceShape = "female".equals(g) ? "鹅蛋脸" : "方脸";
        String skinTone = "中性皮";
        try {
            String raw = callVision(photoUrl);
            JsonNode node = parseJson(raw);
            if (node != null) {
                String f = node.path("faceShape").asText("");
                String s = node.path("skinTone").asText("");
                if (!f.isBlank()) {
                    faceShape = f.trim();
                }
                if (!s.isBlank()) {
                    skinTone = s.trim();
                }
            }
        } catch (Exception e) {
            // 模型不可用时用默认值，不阻断主流程
            log.warn("脸型分析失败，使用默认值：{}", e.getMessage());
        }

        List<Map<String, Object>> styles = rankStyles(g, faceShape);
        List<Map<String, Object>> colors = rankColors(faceShape, skinTone);

        Recommendation row = new Recommendation();
        row.setUserId(userId);
        row.setPhotoUrl(photoUrl);
        row.setGender(g);
        row.setFaceShape(faceShape);
        row.setSkinTone(skinTone);
        row.setCreatedAt(LocalDateTime.now());
        try {
            row.setStylesJson(objectMapper.writeValueAsString(styles));
            row.setColorsJson(objectMapper.writeValueAsString(colors));
        } catch (Exception e) {
            row.setStylesJson("[]");
            row.setColorsJson("[]");
        }
        recommendationMapper.insert(row);
        return toData(row);
    }

    /** 把推荐记录转成前端需要的结构 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toData(Recommendation row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recommendId", row.getId());
        data.put("photoUrl", row.getPhotoUrl());
        data.put("gender", row.getGender());
        data.put("faceShape", row.getFaceShape());
        data.put("skinTone", row.getSkinTone());
        data.put("freeTop", FREE_TOP);
        try {
            data.put("styles", row.getStylesJson() == null || row.getStylesJson().isBlank()
                    ? new ArrayList<>() : objectMapper.readValue(row.getStylesJson(), List.class));
            data.put("colors", row.getColorsJson() == null || row.getColorsJson().isBlank()
                    ? new ArrayList<>() : objectMapper.readValue(row.getColorsJson(), List.class));
        } catch (Exception e) {
            data.put("styles", new ArrayList<>());
            data.put("colors", new ArrayList<>());
        }
        return data;
    }

    /** 读取已缓存的推荐（结果页/发色页复用，避免重复分析） */
    public Recommendation get(Long id) {
        if (id == null) {
            return null;
        }
        Recommendation row = recommendationMapper.selectById(id);
        if (row == null || !Long.valueOf(UserContext.require()).equals(row.getUserId())) {
            return null;
        }
        return row;
    }

    /** 发色列表（含已生成的发型预览 variants），供发色页轮询刷新小图 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> colorsOf(Long id) {
        Recommendation row = get(id);
        if (row == null || row.getColorsJson() == null || row.getColorsJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(row.getColorsJson(), List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> rankStyles(String gender, String faceShape) {
        List<Hairstyle> list = hairstyleMapper.selectList(new QueryWrapper<Hairstyle>()
                .eq("gender", gender)
                .eq("status", 1)
                .orderByAsc("sort"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Hairstyle s : list) {
            int score = scoreStyle(s, faceShape);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("styleId", s.getId());
            item.put("name", s.getName());
            item.put("len", s.getLen());
            item.put("face", s.getFace());
            item.put("cat", s.getCat());
            item.put("score", score);
            item.put("reason", reasonOf(s, faceShape));
            items.add(item);
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> m) -> (Integer) m.get("score")).reversed()
                .thenComparing(m -> String.valueOf(m.get("styleId"))));
        // 分数由「脸型匹配 + 稳定的个性扰动」得出，不再按排名固定 99、98、97 递减
        for (int i = 0; i < items.size(); i++) {
            items.get(i).put("rank", i + 1);
        }
        // 控制返回体大小：发型最多 9 款（前 3 免费 + 6 款待解锁）
        return items.size() > 9 ? new ArrayList<>(items.subList(0, 9)) : items;
    }

    /** 适配分：脸型匹配为主，叠加与发型 ID 绑定的稳定扰动，保证同一用户每次看到同样的分数 */
    private int scoreStyle(Hairstyle s, String faceShape) {
        int score = 88;
        String face = s.getFace() == null ? "" : s.getFace();
        if (faceShape != null && !faceShape.isBlank() && face.contains(faceShape)) {
            score += 8;
        } else if (face.contains("所有")) {
            score += 4;
        }
        // 稳定扰动：不随排名递减，避免出现 99/98/97 这类明显的模板感
        score += Math.floorMod((s.getId() + "|" + faceShape).hashCode(), 4);
        String cat = s.getCat() == null ? "" : s.getCat();
        if (cat.contains("卷") || cat.contains("波波")) {
            score += 1;
        }
        return Math.max(88, Math.min(99, score));
    }

    private String reasonOf(Hairstyle s, String faceShape) {
        String len = s.getLen() == null || s.getLen().isBlank() ? "适中长度" : s.getLen();
        String face = s.getFace() == null || s.getFace().isBlank() ? "多数脸型" : s.getFace();
        if (faceShape != null && face.contains(faceShape)) {
            return "与" + faceShape + "高度匹配，" + len + "显脸小";
        }
        return "适配" + face + "，" + len + "修饰下颌线";
    }

    /** 发色排名：按肤色冷暖匹配，再叠加与色号绑定的稳定扰动，分数不按排名递减 */
    private List<Map<String, Object>> rankColors(String faceShape, String skinTone) {
        List<HairColor> colors = hairColorMapper.selectList(new QueryWrapper<HairColor>().orderByAsc("sort"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (HairColor c : colors) {
            int score = 88 + matchColor(c, skinTone)
                    + Math.floorMod((c.getId() + "|" + skinTone).hashCode(), 5);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("colorId", c.getId());
            item.put("name", c.getName());
            item.put("bg", c.getBg());
            item.put("score", Math.max(88, Math.min(99, score)));
            item.put("reason", colorReason(c, skinTone));
            items.add(item);
        }
        items.sort(Comparator
                .comparingInt((Map<String, Object> m) -> (Integer) m.get("score")).reversed()
                .thenComparing(m -> String.valueOf(m.get("colorId"))));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).put("rank", i + 1);
        }
        // 发色最多 6 款（前 3 免费 + 3 款待解锁）
        return items.size() > 6 ? new ArrayList<>(items.subList(0, 6)) : items;
    }

    private int matchColor(HairColor c, String skinTone) {
        String name = (c.getName() == null ? "" : c.getName()) + (c.getEn() == null ? "" : c.getEn());
        boolean warm = skinTone != null && (skinTone.contains("暖") || skinTone.contains("黄") || skinTone.contains("小麦"));
        boolean cool = skinTone != null && (skinTone.contains("冷") || skinTone.contains("白"));
        if (warm) {
            return name.contains("棕") || name.contains("栗") || name.contains("暖") ? 6 : 0;
        }
        if (cool) {
            return name.contains("黑") || name.contains("冷") || name.contains("灰") || name.contains("茶") ? 6 : 0;
        }
        return name.contains("黑") || name.contains("棕") ? 3 : 0;
    }

    private String colorReason(HairColor c, String skinTone) {
        String tone = skinTone == null || skinTone.isBlank() ? "中性皮" : skinTone;
        String base = colorBaseReason(c);
        if (tone.contains("暖") || tone.contains("黄") || tone.contains("小麦")) {
            return base + "；暖皮也能压住暗沉";
        }
        if (tone.contains("冷") || tone.contains("白")) {
            return base + "；冷白皮更显通透";
        }
        return base + "；中性皮轻松驾驭";
    }

    /** 每款发色给不同的推荐理由，避免所有条目都是同一句 */
    private String colorBaseReason(HairColor c) {
        return switch (c.getId() == null ? "" : c.getId()) {
            case "c1" -> "低饱和黑茶调，显白不挑妆，发质看起来更柔顺";
            case "c2" -> "栗棕暖调提气色，素颜也显精神，冬天更耐看";
            case "c3" -> "奶茶棕柔光感强，温柔显白，通勤约会都合适";
            case "c4" -> "内层挂耳挑染增加层次，不用全头漂也很有辨识度";
            case "c5" -> "巴黎画染渐变显发量，阳光下层次更明显";
            case "c6" -> "粉棕带一点红调，提气色不夸张，显白有记忆点";
            case "c7" -> "雾霾蓝冷调中和黄气，个性但不张扬";
            case "c8" -> "树莓红提气色，偏白肤色更出彩，辨识度高";
            default -> {
                String name = c.getName() == null || c.getName().isBlank() ? "这款发色" : c.getName();
                yield name + "色调自然，日常百搭不挑人";
            }
        };
    }

    private String callVision(String photoUrl) throws Exception {
        AiConfig cfg = configService.get();
        if (cfg.getImageApiKey() == null || cfg.getImageApiKey().isBlank()) {
            throw new IllegalStateException("未配置视觉模型密钥");
        }
        String dataUri = imageService.toDataUri(photoUrl);
        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", PROMPT));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUri)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("temperature", 0);
        body.put("max_tokens", 120);

        String endpoint = trimSlash(cfg.getImageBaseUrl()) + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(visionTimeout))
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

    private JsonNode parseJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(content.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
