package com.fajing.controller;

import com.fajing.service.FileAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * 本地图片访问：替代原来的 /uploads/** 静态映射。
 * ai_ 生成图需要短时签名，其余公开图片直接返回。
 */
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileAccessService fileAccessService;

    @Value("${fajing.upload-dir}")
    private String uploadDir;

    @GetMapping("/uploads/{name:.+}")
    public ResponseEntity<byte[]> file(@PathVariable String name,
                                       @RequestParam(name = "e", required = false) Long exp,
                                       @RequestParam(name = "s", required = false) String signature)
            throws IOException {
        if (name.contains("..") || !name.matches("[A-Za-z0-9._-]+")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        boolean privateFile = FileAccessService.isPrivate(name);
        if (privateFile && !fileAccessService.verify(name, exp, signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "图片链接已失效，请刷新页面");
        }
        File file = new File(uploadDir, name);
        if (!file.exists() || !file.isFile()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在");
        }
        byte[] bytes = Files.readAllBytes(file.toPath());
        CacheControl cache = privateFile
                ? CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate()
                : CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic();
        return ResponseEntity.ok()
                .contentType(mediaType(name))
                .cacheControl(cache)
                .contentLength(bytes.length)
                .body(bytes);
    }

    private static MediaType mediaType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.IMAGE_JPEG;
    }
}
