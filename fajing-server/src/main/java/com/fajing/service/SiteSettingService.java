package com.fajing.service;

import com.fajing.common.BizException;
import com.fajing.entity.SiteSetting;
import com.fajing.mapper.SiteSettingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 站点基础设置：单行配置，缺失时按默认值初始化，供前台与后台共用 */
@Service
@RequiredArgsConstructor
public class SiteSettingService {

    private static final Long SETTING_ID = 1L;

    private final SiteSettingMapper siteSettingMapper;

    public SiteSetting get() {
        SiteSetting setting = siteSettingMapper.selectById(SETTING_ID);
        if (setting == null) {
            setting = new SiteSetting();
            setting.setId(SETTING_ID);
            setting.setHeroTitleMain("换个发型");
            setting.setHeroTitleAccent("换张脸");
            setting.setContactWechat("fajing-ai");
            setting.setContactPhone("");
            setting.setAboutContent("发镜 AI 专注 AI 换发型预览，上传一张正脸照就能看到自己换发型后的样子。");
            setting.setTermsContent("## 服务说明\n\n发镜 AI 提供 AI 换发型预览服务，生成结果仅供造型参考。");
            setting.setPrivacyContent("## 我们收集的信息\n\n你主动上传的人脸照片仅用于本次生成，生成结束 24 小时内自动删除。");
            setting.setFaceConsentText("我同意收集并处理我上传的人脸照片（敏感个人信息），仅用于本次 AI 换发预览，不建人脸库、不用于训练，生成结束 24 小时内删除原图。");
            setting.setUpdatedAt(LocalDateTime.now());
            setting.setUpdatedBy(0L);
            siteSettingMapper.insert(setting);
        }
        return setting;
    }

    public SiteSetting save(SiteSetting req, Long adminId) {
        SiteSetting setting = get();
        if (req.getHeroTitleMain() != null) {
            setting.setHeroTitleMain(shortText("首页标题第一行", req.getHeroTitleMain(), 64));
        }
        if (req.getHeroTitleAccent() != null) {
            setting.setHeroTitleAccent(shortText("首页标题强调行", req.getHeroTitleAccent(), 64));
        }
        if (req.getContactWechat() != null) {
            setting.setContactWechat(shortText("客服微信", req.getContactWechat(), 64));
        }
        if (req.getContactPhone() != null) {
            setting.setContactPhone(shortText("客服电话", req.getContactPhone(), 32));
        }
        if (req.getAboutContent() != null) {
            setting.setAboutContent(req.getAboutContent().trim());
        }
        if (req.getTermsContent() != null) {
            setting.setTermsContent(req.getTermsContent().trim());
        }
        if (req.getPrivacyContent() != null) {
            setting.setPrivacyContent(req.getPrivacyContent().trim());
        }
        if (req.getFaceConsentText() != null) {
            setting.setFaceConsentText(req.getFaceConsentText().trim());
        }
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(adminId == null ? 0L : adminId);
        siteSettingMapper.updateById(setting);
        return setting;
    }

    private String shortText(String label, String value, int max) {
        String text = value.trim();
        if (text.length() > max) {
            throw new BizException(label + "不能超过 " + max + " 个字符");
        }
        return text;
    }
}
