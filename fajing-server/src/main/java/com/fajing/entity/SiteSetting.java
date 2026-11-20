package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站点基础设置：单行配置，前台文案与联系方式由此下发 */
@Data
@TableName("site_setting")
public class SiteSetting {
    /** 固定单行主键，必须显式写入 1 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 首页大标题第一行 */
    private String heroTitleMain;
    /** 首页大标题强调行 */
    private String heroTitleAccent;
    /** 客服微信号 */
    private String contactWechat;
    /** 客服电话 */
    private String contactPhone;
    /** 关于我们正文，纯文本分段，## 开头为小标题 */
    private String aboutContent;
    /** 用户协议正文 */
    private String termsContent;
    /** 隐私政策正文 */
    private String privacyContent;
    /** 人脸信息单独同意文案 */
    private String faceConsentText;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
