package com.fajing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fajing.common.BizException;
import com.fajing.entity.AiConfig;
import com.fajing.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图像编辑服务：调用 OpenAI 兼容的 images/generations 接口（默认硅基流动 Qwen-Image-Edit）
 * 换模型只需在后台改 baseUrl / model / key，无需改代码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    /** 送给模型前把原图缩到这个长边，兼顾质量与请求体大小 */
    private static final int MAX_EDGE = 1280;

    private final AiConfigService configService;
    private final StorageService storageService;

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            // 参考图/结果图常带 301/302 跳转，必须跟随，否则下载失败
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /**
     * 对一张照片执行「换发型」编辑，返回落盘后的可访问 URL
     *
     * @param referenceUrl 目标发型参考图（可选），Qwen-Image-Edit-2509 支持 image2 多图输入
     */
    public String editHair(String photoUrl, String referenceUrl, String prompt, Long taskId, String styleId) throws Exception {
        AiConfig cfg = configService.get();
        if (cfg.getEnabled() == null || cfg.getEnabled() != 1) {
            throw new BizException("AI 出图未启用");
        }
        if (cfg.getImageApiKey() == null || cfg.getImageApiKey().isBlank()) {
            throw new BizException("未配置图像模型 API Key，请在管理端「AI 设置」或环境变量 SILICONFLOW_KEY 中配置");
        }

        String dataUri = toDataUri(photoUrl);
        // 参考图在服务端转 base64 再传，避免模型侧拉取外链失败
        String refDataUri = (referenceUrl == null || referenceUrl.isBlank()) ? null : toPlainDataUri(referenceUrl);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getImageModel());
        body.put("prompt", prompt);
        body.put("image", dataUri);
        boolean withRef = refDataUri != null;
        if (withRef) {
            // 图2 = 发型参考图，模型照着图2换发，比纯文字更稳
            body.put("image2", refDataUri);
        }
        // 抑制改五官/美颜倾向；部分编辑模型不支持该参数，下面会降级重试
        body.put("negative_prompt", AiConfigService.DEFAULT_NEGATIVE_PROMPT);

        String endpoint = trimSlash(cfg.getImageBaseUrl()) + "/images/generations";
        HttpResponse<String> resp = post(endpoint, cfg.getImageApiKey(), body);
        if (resp.statusCode() == 400) {
            log.warn("图像模型不接受 negative_prompt，去掉后重试");
            body.remove("negative_prompt");
            resp = post(endpoint, cfg.getImageApiKey(), body);
        }
        if (resp.statusCode() != 200 && withRef) {
            log.warn("图像模型不接受参考图，去掉 image2 重试：{}", shorten(resp.body()));
            body.remove("image2");
            resp = post(endpoint, cfg.getImageApiKey(), body);
        }
        if (resp.statusCode() != 200) {
            throw new BizException("图像模型调用失败（" + resp.statusCode() + "）：" + shorten(resp.body()));
        }

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode urlNode = root.path("images").path(0).path("url");
        if (urlNode.isMissingNode() || urlNode.asText().isBlank()) {
            throw new BizException("图像模型未返回图片：" + shorten(resp.body()));
        }
        return download(urlNode.asText(), taskId, styleId);
    }

    private HttpResponse<String> post(String endpoint, String apiKey, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(180))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /* ---------- 原图 → data URI ---------- */
    private String toDataUri(String photoUrl) throws Exception {
        byte[] raw = null;
        String name = fileNameOf(photoUrl);
        if (!name.isBlank()) {
            File f = new File(uploadDir, name);
            if (f.exists() && f.isFile()) {
                raw = Files.readAllBytes(f.toPath());
            }
        }
        if (raw == null && photoUrl != null && photoUrl.startsWith("http")) {
            raw = httpGet(photoUrl);
        }
        if (raw == null || raw.length == 0) {
            throw new BizException("读取上传照片失败");
        }

        byte[] jpeg = compressToJpeg(raw);
        String b64 = Base64.getEncoder().encodeToString(jpeg);
        return "data:image/jpeg;base64," + b64;
    }

    /** 外部参考图 → data URI，失败返回 null（此时退化为纯文生图编辑） */
    private String toPlainDataUri(String url) {
        try {
            byte[] raw = httpGet(url);
            if (raw == null || raw.length == 0) {
                return null;
            }
            byte[] jpeg = compressToJpeg(raw);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg);
        } catch (Exception e) {
            log.warn("参考图下载失败，忽略参考图: {}", e.getMessage());
            return null;
        }
    }

    /** 缩放并转 JPEG，读不了的格式（如 webp）原样返回 */
    private byte[] compressToJpeg(byte[] raw) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) {
                return raw;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(w, h));
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));

            BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("图片压缩失败，使用原图: {}", e.getMessage());
            return raw;
        }
    }

    /* ---------- 结果图下载落盘，避免 URL 过期 ---------- */
    private String download(String url, Long taskId, String styleId) throws Exception {
        byte[] bytes = httpGet(url);
        if (bytes == null || bytes.length == 0) {
            throw new BizException("下载生成图失败");
        }
        return storageService.put(bytes, "png",
                "ai_" + taskId + "_" + styleId + "_" + System.currentTimeMillis());
    }

    private byte[] httpGet(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new BizException("图片下载失败（" + resp.statusCode() + "）");
        }
        return resp.body();
    }

    private static String fileNameOf(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        String clean = q > -1 ? url.substring(0, q) : url;
        int slash = clean.lastIndexOf('/');
        return slash > -1 ? clean.substring(slash + 1) : "";
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
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
