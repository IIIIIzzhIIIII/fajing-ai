package com.fajing.util;

import com.fajing.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 商户私钥文件落盘：只保存路径，不把私钥内容写进数据库。
 * 私钥必须放在静态上传目录之外，否则会被 /uploads/** 公开下载；
 * 默认使用 upload-dir 的同级目录 fajing-cert，可用 fajing.cert-dir 覆盖。
 */
@Component
public class KeyFileStore {

    private static final long MAX_SIZE = 64 * 1024L;

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    @Value("${fajing.cert-dir:}")
    private String certDir;

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择私钥文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!name.toLowerCase().endsWith(".pem")) {
            throw new BizException("仅支持 .pem 格式的私钥文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException("私钥文件不能超过 64KB");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            if (!content.contains("PRIVATE KEY")) {
                throw new BizException("文件内容不是有效的私钥（缺少 PRIVATE KEY）");
            }
            Path dir = resolveDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID().toString().replace("-", "") + ".pem");
            Files.write(target, file.getBytes());
            return target.toAbsolutePath().toString().replace('\\', '/');
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("私钥保存失败：" + e.getMessage());
        }
    }

    private Path resolveDir() {
        if (certDir != null && !certDir.isBlank()) {
            return Paths.get(certDir.trim()).toAbsolutePath().normalize();
        }
        Path upload = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path parent = upload.getParent();
        return (parent == null ? upload : parent).resolve("fajing-cert");
    }
}
