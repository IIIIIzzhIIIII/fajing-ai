package com.fajing.service;

import com.fajing.entity.AiConfig;
import com.fajing.mapper.AiConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 配置读取/保存：数据库优先，缺省值来自 application.yml
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private static final Long CONFIG_ID = 1L;

    private final AiConfigMapper aiConfigMapper;

    @Value("${fajing.ai.image-base-url:https://api.siliconflow.cn/v1}")
    private String defaultImageBaseUrl;
    @Value("${fajing.ai.image-api-key:}")
    private String defaultImageApiKey;
    @Value("${fajing.ai.image-model:Qwen/Qwen-Image-Edit-2509}")
    private String defaultImageModel;
    @Value("${fajing.ai.text-base-url:https://api.deepseek.com/v1}")
    private String defaultTextBaseUrl;
    @Value("${fajing.ai.text-api-key:}")
    private String defaultTextApiKey;
    @Value("${fajing.ai.text-model:deepseek-flash}")
    private String defaultTextModel;
    @Value("${fajing.ai.use-text-enhance:0}")
    private Integer defaultUseTextEnhance;

    public AiConfig get() {
        AiConfig cfg = aiConfigMapper.selectById(CONFIG_ID);
        if (cfg == null) {
            cfg = defaults();
            try {
                aiConfigMapper.insert(cfg);
            } catch (Exception e) {
                log.warn("AI 配置初始化失败，使用内存默认值: {}", e.getMessage());
            }
        }
        // 数据库里 key 为空时回落到配置文件
        if (isBlank(cfg.getImageApiKey())) {
            cfg.setImageApiKey(defaultImageApiKey);
        }
        if (isBlank(cfg.getTextApiKey())) {
            cfg.setTextApiKey(defaultTextApiKey);
        }
        return cfg;
    }

    public AiConfig save(AiConfig req) {
        AiConfig cfg = aiConfigMapper.selectById(CONFIG_ID);
        if (cfg == null) {
            cfg = defaults();
            copy(req, cfg);
            cfg.setUpdatedAt(LocalDateTime.now());
            aiConfigMapper.insert(cfg);
            return cfg;
        }
        copy(req, cfg);
        cfg.setUpdatedAt(LocalDateTime.now());
        aiConfigMapper.updateById(cfg);
        return cfg;
    }

    private AiConfig defaults() {
        AiConfig cfg = new AiConfig();
        cfg.setId(CONFIG_ID);
        cfg.setImageProvider("siliconflow");
        cfg.setImageBaseUrl(defaultImageBaseUrl);
        cfg.setImageApiKey(defaultImageApiKey);
        cfg.setImageModel(defaultImageModel);
        cfg.setTextBaseUrl(defaultTextBaseUrl);
        cfg.setTextApiKey(defaultTextApiKey);
        cfg.setTextModel(defaultTextModel);
        cfg.setPromptTemplate(DEFAULT_PROMPT);
        cfg.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
        cfg.setUseTextEnhance(defaultUseTextEnhance == null ? 0 : defaultUseTextEnhance);
        cfg.setEnabled(1);
        cfg.setUpdatedAt(LocalDateTime.now());
        return cfg;
    }

    private void copy(AiConfig from, AiConfig to) {
        if (!isBlank(from.getImageProvider())) to.setImageProvider(from.getImageProvider());
        if (!isBlank(from.getImageBaseUrl())) to.setImageBaseUrl(from.getImageBaseUrl().trim());
        // 脱敏值（含 *）不回写，避免把掩码当成真实 key 存库
        if (!isBlank(from.getImageApiKey()) && !from.getImageApiKey().contains("*")) {
            to.setImageApiKey(from.getImageApiKey().trim());
        }
        if (!isBlank(from.getImageModel())) to.setImageModel(from.getImageModel().trim());
        if (!isBlank(from.getTextBaseUrl())) to.setTextBaseUrl(from.getTextBaseUrl().trim());
        if (!isBlank(from.getTextApiKey()) && !from.getTextApiKey().contains("*")) {
            to.setTextApiKey(from.getTextApiKey().trim());
        }
        if (!isBlank(from.getTextModel())) to.setTextModel(from.getTextModel().trim());
        if (from.getPromptTemplate() != null) to.setPromptTemplate(from.getPromptTemplate());
        if (from.getSystemPrompt() != null) to.setSystemPrompt(from.getSystemPrompt());
        if (from.getUseTextEnhance() != null) to.setUseTextEnhance(from.getUseTextEnhance());
        if (from.getEnabled() != null) to.setEnabled(from.getEnabled());
    }

    /** 返回给管理端时脱敏：只露前 4 后 4，避免接口明文回传密钥 */
    public AiConfig masked(AiConfig cfg) {
        AiConfig view = new AiConfig();
        if (cfg == null) {
            return view;
        }
        copy(cfg, view);
        view.setId(cfg.getId());
        view.setImageApiKey(mask(cfg.getImageApiKey()));
        view.setTextApiKey(mask(cfg.getTextApiKey()));
        view.setUpdatedAt(cfg.getUpdatedAt());
        return view;
    }

    private String mask(String key) {
        if (isBlank(key)) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 默认图像编辑指令，后台可修改 */
    public static final String DEFAULT_PROMPT =
            "【任务】只更换头发，其他一切必须保持不变。\n"
                    + "目标发型：{style}（{en}），长度{len}，修饰{face}。{color}\n\n"
                    + "【发色】除非明确要求染发，头发颜色必须与原图一致（通常为纯黑色）；不要改成蓝色、灰色、冷灰色或其他颜色。"
                    + "Hair must remain pure black like the original photo, no blue or grey tint. "
                    + "图2只参考发型轮廓、长度与层次，不要复制图2的发色。\n\n"
                    + "【必须原样保留，禁止改动】同一个人：眉毛（形状/粗细/浓淡）、眼睛大小与单双眼皮、鼻子、嘴唇、耳朵、"
                    + "脸型轮廓（颧骨/下颌角/下巴）、肤色、皮肤真实质感（毛孔、纹理、阴影、痣）、年龄感、脖子、衣服、背景、机位、光线完全不变。"
                    + "禁止美颜、磨皮、提亮美白肤色、瘦脸、放大眼睛、修改眉形、换脸。\n"
                    + "Keep the same person: do not change eyebrows, eyes, nose, mouth, jawline, face shape, "
                    + "skin tone or skin texture. Keep the original hair color (black). No beauty retouching, no face swap.\n\n"
                    + "【表情必须完全一致】保留原图的表情和嘴部状态：原图在笑就必须是同样的笑容，原图露齿就必须继续露齿；"
                    + "嘴角弧度、嘴巴开合程度、牙齿露出多少都要与原图一致。不要把露齿笑改成闭嘴微笑，不要换表情、不要改变眼神。\n"
                    + "Do not change the facial expression: keep exactly the same smile, mouth opening and teeth visibility as the original photo. "
                    + "Never close the mouth, never hide the teeth, never change the look in the eyes.\n\n"
                    + "【只允许修改头发】按 {en} 的造型换发，长度保持{len}；刘海轻薄并尽量分开、露出眉毛。\n"
                    + "【风格】必须是当下流行的现代发型（2024–2025 韩系 / 日系 / 现代欧美剪裁），轻盈、有空气感和层次；"
                    + "不要 80/90 年代复古造型，不要厚重贴头皮，不要老派中分大背头。\n\n"
                    + "【真实感要求】这是真实摄影照片而不是贴图：发丝根根分明、有高光与阴影层次，发根贴合头皮，"
                    + "发际线与鬓角有自然碎发过渡，头发边缘与原图光照方向和景深一致；不要假发感、不要塑料感、不要边缘生硬。";

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一名资深发型设计师兼 AI 图像编辑提示词工程师。"
                    + "用户会给你目标发型信息，你要输出一段用于图像编辑模型（Qwen-Image-Edit）的中文指令。"
                    + "硬性要求：只更换头发，绝对不能改变眉毛、眼睛、鼻子、嘴巴、耳朵、脸型、肤色、年龄感、服装与背景；"
                    + "表情必须与原图完全一致（笑容、嘴巴开合、是否露齿、眼神都不能变）；"
                    + "不得美颜、磨皮、瘦脸、放大眼睛、修眉或换脸，必须是同一个人。"
                    + "发型长度必须与目标一致，不得擅自加长或改成其他发型；同时给出英文造型关键词。"
                    + "只输出指令正文，不要解释、不要加引号。";

    /** 负面提示词：抑制改五官/美颜/改表情类倾向（模型不支持时会自动降级重试） */
    public static final String DEFAULT_NEGATIVE_PROMPT =
            "changed eyebrows, different eyebrows, altered facial features, different person, face swap, "
                    + "beauty retouch, skin smoothing, slim face, bigger eyes, changed expression, "
                    + "changed smile, mouth closed, hidden teeth, no teeth, different eyes, "
                    + "brightened skin, whiter skin, fairer skin, changed clothes, changed background, distorted face, "
                    + "blue hair, grey hair, colored hair, wig, hairpiece, sticker, artificial hair, plastic hair, "
                    + "flat hair, fake hairline, harsh edges, 90s hairstyle, 80s hairstyle, dated hairstyle, "
                    + "old fashioned hair, heavy thick hair, helmet hair, bowl cut, greasy hair, "
                    + "改眉形, 换脸, 瘦脸, 放大眼睛, 美颜, 磨皮, 美白, 提亮肤色, 改变五官, 改变肤色, 改变服装, 改变背景, "
                    + "笑容改变, 闭嘴, 不露齿, 表情改变, 换表情, 眼神改变, "
                    + "假发, 贴图, 塑料感, 发丝生硬, 蓝色头发, 灰色头发, 复古发型, 老气, 厚重头发";
}
