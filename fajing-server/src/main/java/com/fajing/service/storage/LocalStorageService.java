package com.fajing.service.storage;

import com.fajing.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/**
 * 本地磁盘存储：写入 ${fajing.upload-dir}，通过 /uploads/** 静态映射访问
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    @Value("${fajing.base-url}")
    private String baseUrl;

    @Override
    public String put(byte[] bytes, String ext, String prefix) {
        try {
            File dir = new File(uploadDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new BizException("上传目录创建失败");
            }
            String safeExt = (ext == null || ext.isBlank()) ? "jpg" : ext.toLowerCase();
            String name = (prefix == null || prefix.isBlank() ? UUID.randomUUID().toString().replace("-", "") : prefix)
                    + "." + safeExt;
            Files.write(new File(dir, name).toPath(), bytes);
            return trimSlash(baseUrl) + "/uploads/" + name;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("本地存储写入失败", e);
            throw new BizException("图片保存失败");
        }
    }

    @Override
    public void delete(String url) {
        String name = nameOf(url);
        if (name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return;
        }
        File file = new File(uploadDir, name);
        if (file.exists() && !file.delete()) {
            log.warn("删除文件失败：{}", file.getAbsolutePath());
        }
    }

    private String nameOf(String url) {
        if (url == null) {
            return "";
        }
        int q = url.indexOf('?');
        String clean = q > -1 ? url.substring(0, q) : url;
        int marker = clean.indexOf("/uploads/");
        if (marker < 0) {
            return "";
        }
        int slash = clean.lastIndexOf('/');
        return clean.substring(slash + 1);
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
