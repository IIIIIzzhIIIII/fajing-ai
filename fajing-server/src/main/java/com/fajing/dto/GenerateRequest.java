package com.fajing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建换发任务请求")
public class GenerateRequest {
    @Schema(description = "上传后的正脸照片 URL", example = "http://localhost:8081/uploads/a.jpg")
    private String photoUrl;

    @Schema(description = "性别：female / male", example = "female")
    private String gender = "female";

    @Schema(description = "生成模式：hair 仅换发型，hairColor 换发型+发色", example = "hair")
    private String mode = "hair";

    @Schema(description = "发色 ID，可为空", example = "")
    private String colorId = "";

    @Schema(description = "要生成的发型 ID 列表", example = "[\"f001\",\"f002\"]")
    @NotEmpty(message = "请先选择发型")
    private List<String> styleIds;

    @Schema(description = "推荐记录 ID，用于透出适配分与推荐依据", example = "1")
    private Long recommendId;
}
