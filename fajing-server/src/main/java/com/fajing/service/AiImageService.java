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
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
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

    /** 染发模式负面词：必须排除 colored hair / blue hair / grey hair 等会抑制染色的词 */
    private static final String COLOR_NEGATIVE_PROMPT =
            "changed eyebrows, different eyebrows, altered facial features, different person, face swap, "
                    + "beauty retouch, skin smoothing, slim face, bigger eyes, changed expression, "
                    + "changed smile, mouth closed, hidden teeth, different eyes, "
                    + "brightened skin, whiter skin, fairer skin, changed clothes, changed background, distorted face, "
                    + "wig, hairpiece, sticker, artificial hair, plastic hair, flat hair, fake hairline, harsh edges, "
                    + "wrong hair color, uneven hair color, color patches, gradient hair, black roots, two-tone hair, "
                    + "改眉形, 换脸, 瘦脸, 放大眼睛, 美颜, 磨皮, 美白, 提亮肤色, 改变五官, 改变肤色, 改变服装, 改变背景, "
                    + "笑容改变, 闭嘴, 不露齿, 表情改变, 换表情, 眼神改变, "
                    + "假发, 贴图, 塑料感, 发丝生硬, 发色不均, 黑发根";

    private final AiConfigService configService;
    private final StorageService storageService;

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    /** 本站域名与 S3 公网域名：用户照片只允许从这两个来源拉取，防止 SSRF */
    @Value("${fajing.base-url:}")
    private String baseUrl;

    @Value("${storage.s3.public-base-url:}")
    private String s3PublicBaseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            // 参考图/结果图常带 301/302 跳转，必须跟随，否则下载失败
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    /** 用户照片下载：禁止跳转，避免白名单域名 302 到内网地址 */
    private final HttpClient photoHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 对一张照片执行「换发型」编辑，返回落盘后的可访问 URL
     *
     * @param referenceUrl 目标发型参考图（可选），Qwen-Image-Edit-2509 支持 image2 多图输入
     */
    public String editHair(String photoUrl, String referenceUrl, String prompt, Long taskId, String styleId) throws Exception {
        return edit(photoUrl, referenceUrl, prompt, "ai_" + taskId + "_" + styleId + "_", true, null, false);
    }

    /**
     * 染发模式：发色是本次必须执行的变更，因此不能沿用默认负面词里的
     * colored hair / blue hair / grey hair（会抑制染发）。
     */
    public String editHairColor(String photoUrl, String referenceUrl, String prompt, Long taskId, String styleId,
                                String colorEn) throws Exception {
        String extraNegative = "wrong hair color, uneven hair color, color patches, gradient hair, "
                + "black roots, two-tone hair, keep original black hair, wig, plastic hair, "
                + "发色不均, 黑发根, 假发";
        // 同一发型的多个发色变体并发生成时，文件名带上发色标识避免互相覆盖
        String colorTag = colorEn == null || colorEn.isBlank()
                ? "color" : Integer.toHexString(colorEn.hashCode());
        return edit(photoUrl, referenceUrl, prompt, "ai_" + taskId + "_" + styleId + "_" + colorTag + "_",
                true, extraNegative, false, COLOR_NEGATIVE_PROMPT);
    }

    /**
     * 管理端「标准模特照」：保留人物脸部/五官/发型/表情，只规范取景与姿态，
     * 输出固定 3:4 的正面肩颈像（头顶留少量空隙、下沿在锁骨附近），用于发型库素材。
     */
    public String normalizeModelShot(String imageUrl) throws Exception {
        String prompt = "图1、图2是同一个人。请严格保留人物在图2中的脸部、五官、脸型、发型形状、发长、发色、刘海、卷度、表情和年龄特征，"
                + "不要染发、不要改变发色、不要换发型、不要加刘海或去掉刘海。\n"
                + "【只调整取景与姿态】正面朝向镜头、头部水平不倾斜、脸部居中、平视镜头，双肩对称；"
                + "头顶距画面上边缘约 5%～8%，下巴位于画面高度约 60% 处，下沿裁剪在锁骨与肩膀上缘，"
                + "只露出脖子和肩膀，不露出胸部或身体；脸部宽度约占画面宽度的 45%～55%。\n"
                + "【画面规格】3:4 竖构图，纯净浅灰（约 #EAEAEA）纯色背景，无纹理、无杂物、无文字；"
                + "柔和均匀的正面摄影棚光，真实摄影质感，高清细节，发丝根根分明。\n"
                + "不要美颜、不要换脸、不要改变脸型、不要改变表情、不要添加任何文字或水印。";
        String extraNegative = "full body, wide shot, showing chest, bare chest, too much shoulder, "
                + "cropped head, tilted head, side profile, off-center face, busy background, "
                + "text, watermark, logo, border, frame";
        return edit(imageUrl, imageUrl, prompt, "ai_model_", false, extraNegative, true);
    }

    private String edit(String photoUrl, String referenceUrl, String prompt,
                        String prefix, boolean withWatermark, String extraNegative, boolean portrait) throws Exception {
        return edit(photoUrl, referenceUrl, prompt, prefix, withWatermark, extraNegative, portrait,
                AiConfigService.DEFAULT_NEGATIVE_PROMPT);
    }

    private String edit(String photoUrl, String referenceUrl, String prompt,
                        String prefix, boolean withWatermark, String extraNegative, boolean portrait,
                        String negativeBase) throws Exception {
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
        String negative = negativeBase;
        if (extraNegative != null && !extraNegative.isBlank()) {
            negative = negative + ", " + extraNegative;
        }
        body.put("negative_prompt", negative);

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
        return download(urlNode.asText(), prefix, withWatermark, portrait);
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
    public String toDataUri(String photoUrl) throws Exception {
        byte[] raw = null;
        String name = fileNameOf(photoUrl);
        // 仅允许读取上传目录内的普通文件名，避免 photoUrl 中携带 ..\ 等片段造成路径穿越
        if (isSafeUploadName(name)) {
            File f = new File(uploadDir, name);
            if (f.exists() && f.isFile()) {
                raw = Files.readAllBytes(f.toPath());
            }
        }
        if (raw == null && photoUrl != null && photoUrl.startsWith("http")) {
            raw = httpGetPhoto(photoUrl);
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
    private String download(String url, String prefix, boolean withWatermark, boolean portrait) throws Exception {
        byte[] bytes = httpGet(url);
        if (bytes == null || bytes.length == 0) {
            throw new BizException("下载生成图失败");
        }
        byte[] output = withWatermark ? watermark(bytes) : bytes;
        if (portrait) {
            output = toThreeFour(output);
        }
        // 写入 AIGC 隐式标识（PNG tEXt / JPEG COM），满足生成内容标识要求
        output = com.fajing.util.AigcMetadata.mark(output);
        return storageService.put(output, "png", prefix + System.currentTimeMillis());
    }

    /**
     * 统一模特照规格：裁剪/缩放为 3:4（768×1024）。
     * 偏宽的图居中裁两侧；偏高的图保留顶部、裁掉下方多余部分（只留肩颈）。
     */
    private byte[] toThreeFour(byte[] raw) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(raw));
            if (src == null) {
                return raw;
            }
            double targetRatio = 3.0 / 4.0;
            double ratio = (double) src.getWidth() / src.getHeight();
            BufferedImage cropped;
            if (ratio > targetRatio) {
                int w = Math.max(1, (int) Math.round(src.getHeight() * targetRatio));
                int x = Math.max(0, (src.getWidth() - w) / 2);
                cropped = src.getSubimage(x, 0, w, src.getHeight());
            } else if (ratio < targetRatio) {
                int h = Math.max(1, (int) Math.round(src.getWidth() / targetRatio));
                cropped = src.getSubimage(0, 0, src.getWidth(), Math.min(h, src.getHeight()));
            } else {
                cropped = src;
            }

            BufferedImage out = new BufferedImage(768, 1024, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cropped, 0, 0, 768, 1024, null);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("模特照比例规整失败，返回原图: {}", e.getMessage());
            return raw;
        }
    }

    /** 服务端烧「AI 生成」标识：字号取图宽 3%，右下角半透明，避免传播场景无标识 */
    private byte[] watermark(byte[] raw) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(raw));
            if (img == null) {
                return raw;
            }
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int size = Math.max(14, (int) Math.round(img.getWidth() * 0.03));
            Font font = new Font("SansSerif", Font.BOLD, size);
            String text = font.canDisplay('生') ? "AI 生成" : "AI GENERATED";
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int pad = Math.max(10, size / 2);
            int x = img.getWidth() - fm.stringWidth(text) - pad;
            int y = img.getHeight() - pad;
            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(text, x + 1, y + 1);
            g.setColor(new Color(255, 255, 255, 170));
            g.drawString(text, x, y);
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            log.warn("AI 标识合成失败，返回原图: {}", e.getMessage());
            return raw;
        }
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

    /** 用户照片只允许本站 /uploads 或已配置的 S3 公网域名，且不跟随跳转 */
    private byte[] httpGetPhoto(String url) throws Exception {
        if (!isAllowedPhotoUrl(url)) {
            throw new BizException("照片地址不合法");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> resp = photoHttp.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new BizException("图片下载失败（" + resp.statusCode() + "）");
        }
        return resp.body();
    }

    private boolean isAllowedPhotoUrl(String url) {
        try {
            URI target = URI.create(url);
            String scheme = target.getScheme();
            if (scheme == null
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            URI base = parseUri(baseUrl);
            if (base != null && sameOrigin(target, base)
                    && target.getPath() != null && target.getPath().startsWith("/uploads/")) {
                return true;
            }
            URI s3 = parseUri(s3PublicBaseUrl);
            return s3 != null && sameOrigin(target, s3);
        } catch (Exception e) {
            return false;
        }
    }

    private static URI parseUri(String url) {
        try {
            return url == null || url.isBlank() ? null : URI.create(url.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme() != null && b.getScheme() != null
                && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost() != null && b.getHost() != null
                && a.getHost().equalsIgnoreCase(b.getHost())
                && portOf(a) == portOf(b);
    }

    private static int portOf(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
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

    /** 上传目录文件名白名单：字母数字点下划线连字符，且不含 ..（防止 Windows 下反斜杠穿越） */
    private static boolean isSafeUploadName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("..")
                && name.matches("[A-Za-z0-9._-]+");
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
