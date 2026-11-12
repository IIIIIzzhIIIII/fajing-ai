package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成配置（单行，id 固定为 1）
 * 管理后台可编辑，支持随时切换图像模型 / 文案模型 / 提示词
 */
@Data
@TableName("ai_config")
public class AiConfig {
    private Long id;
    /** 图像服务商标识，仅用于展示 */
    private String imageProvider;
    private String imageBaseUrl;
    private String imageApiKey;
    private String imageModel;
    private String textBaseUrl;
    private String textApiKey;
    private String textModel;
    /** 图像编辑指令模板，占位符：{style} {len} {face} {color} */
    private String promptTemplate;
    /** DeepSeek 系统提示词，用于把模板改写成更专业的编辑指令 */
    private String systemPrompt;
    /** 1=启用文案模型增强提示词 0=直接用模板 */
    private Integer useTextEnhance;
    /** 1=启用真实 AI 出图 0=使用占位图 */
    private Integer enabled;
    private LocalDateTime updatedAt;
}
