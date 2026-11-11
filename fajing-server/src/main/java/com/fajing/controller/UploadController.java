package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UploadController {

    private static final Set<String> ALLOWED = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024L;

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    @Value("${fajing.base-url}")
    private String baseUrl;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的照片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("图片不能超过 10MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED.contains(ext)) {
            throw new BizException("仅支持 jpg / png / webp 格式");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new BizException("文件类型不正确");
        }

        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException("上传目录创建失败");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(new File(dir, name));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", baseUrl + "/uploads/" + name);
        data.put("path", name);
        data.put("size", file.getSize());
        return Result.ok(data);
    }
}
