package com.fajing.controller;

import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.service.PhotoCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 上传照片的 AI 审查：只放行单人正脸照，防止用户上传风景、侧脸、多人合照等。
 */
@Tag(name = "照片审查", description = "上传前判断是否为正脸人像")
@RestController
@RequestMapping("/api/photo")
@RequiredArgsConstructor
public class PhotoCheckController {

    private final PhotoCheckService photoCheckService;

    @Operation(summary = "审查照片是否为正脸人像")
    @PostMapping("/check")
    public Result<Map<String, Object>> check(@RequestBody(required = false) Map<String, String> body) {
        String photoUrl = body == null ? null : body.get("photoUrl");
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BizException("缺少照片地址");
        }
        return Result.ok(photoCheckService.check(photoUrl.trim()));
    }
}
