package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AiCallLog;
import com.fajing.entity.AiConfig;
import com.fajing.mapper.AiCallLogMapper;
import com.fajing.service.AiConfigService;
import com.fajing.service.AiImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端 AI 工具：把网上找的图片规范成统一的标准模特照（面向前方、只露脖子与肩膀以上）。
 * 使用管理端「AI 设置」里配置的图像模型，结果落对象存储后可写回发型库。
 */
@Slf4j
@Tag(name = "管理端 · AI", description = "标准模特照生成")
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AiImageService imageService;
    private final AiConfigService aiConfigService;
    private final AiCallLogMapper aiCallLogMapper;

    @Value("${fajing.base-url:http://localhost:8081}")
    private String baseUrl;

    @Value("${ai.cost-cents-per-call:20}")
    private int costCentsPerCall;

    @Operation(summary = "网图 → 标准模特照（保留脸部、五官、发型与表情）")
    @PostMapping("/model-shot")
    public Result<Map<String, Object>> modelShot(@RequestBody(required = false) Map<String, String> body,
                                                 HttpServletRequest request) {
        String imageUrl = body == null ? null : body.get("imageUrl");
        validateImageUrl(imageUrl);
        try {
            String url = imageService.normalizeModelShot(imageUrl.trim());
            logCall(request, true);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("url", url);
            return Result.ok(data);
        } catch (BizException e) {
            logCall(request, false);
            throw e;
        } catch (Exception e) {
            logCall(request, false);
            log.warn("标准模特照生成失败：{}", e.getMessage());
            throw new BizException("生成失败，请稍后重试");
        }
    }

    /** 只允许 http/https；本机上传地址放行，其余内网地址一律拒绝，避免 SSRF */
    private void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new BizException("请填写图片地址或先上传图片");
        }
        URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (Exception e) {
            throw new BizException("图片地址格式不正确");
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new BizException("仅支持 http/https 图片地址");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new BizException("图片地址格式不正确");
        }
        if (isOwnUpload(host, uri.getPath())) {
            return;
        }
        if (isPrivateHost(host)) {
            throw new BizException("不支持内网或本机地址，请使用公网图片");
        }
    }

    /** 只放行本站上传目录（管理端先上传的图片），其他同主机地址不特殊处理 */
    private boolean isOwnUpload(String host, String path) {
        String own = URI.create(baseUrl).getHost();
        return own != null && own.equalsIgnoreCase(host)
                && path != null && path.startsWith("/uploads/");
    }

    private boolean isPrivateHost(String host) {
        String h = host.toLowerCase();
        if ("localhost".equals(h) || h.endsWith(".localhost") || "0.0.0.0".equals(h)
                || "::1".equals(h) || "[::1]".equals(h)) {
            return true;
        }
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.")
                || h.startsWith("169.254.") || h.startsWith("100.64.")) {
            return true;
        }
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private void logCall(HttpServletRequest request, boolean ok) {
        try {
            AiConfig config = aiConfigService.get();
            AiCallLog logRow = new AiCallLog();
            logRow.setTaskId(0L);
            Object adminId = request.getAttribute("adminId");
            logRow.setUserId(adminId instanceof Long value ? value : 0L);
            logRow.setProvider(config == null || config.getImageProvider() == null ? "" : config.getImageProvider());
            logRow.setModel(config == null || config.getImageModel() == null ? "" : config.getImageModel());
            logRow.setCostCents(ok ? costCentsPerCall : 0);
            logRow.setOk(ok);
            logRow.setCreatedAt(LocalDateTime.now());
            aiCallLogMapper.insert(logRow);
        } catch (Exception e) {
            log.debug("AI 调用流水写入失败: {}", e.getMessage());
        }
    }
}
