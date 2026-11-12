package com.fajing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "发布讨论区帖子请求")
public class PostRequest {
    @Schema(description = "发型名称", example = "法式短发")
    private String styleName = "";

    @Schema(description = "正文内容（会做内容安全校验）", example = "剪完感觉精神多了")
    private String content = "";

    @Schema(description = "AI 效果图 URL")
    private String aiImage = "";

    @Schema(description = "剪后实拍图 URL，还没剪时留空")
    private String afterImage = "";

    @Schema(description = "性别：female / male", example = "female")
    private String gender = "female";

    @Schema(description = "real=剪后作业，ask=还没剪求建议", example = "real")
    private String badge = "real";

    @Schema(description = "标签", example = "[\"圆脸友好\"]")
    private List<String> tags;
}
