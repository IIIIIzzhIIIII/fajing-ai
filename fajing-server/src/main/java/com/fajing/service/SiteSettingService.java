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
            setting.setTermsContent("""
                    ## 1. 协议主体与生效

                    本协议由【运营主体全称】（以下简称「我们」）与你（以下简称「你」）就使用「发镜 AI」服务订立。注册地址：【省市区详细地址】；联系邮箱：【support@你的域名】。你勾选同意或实际使用本服务，即表示你已阅读并同意本协议及《隐私政策》。本协议版本 V1.0，生效日期：【填写具体日期】。

                    ## 2. 服务内容

                    发镜 AI 提供基于人工智能的发型预览、付费高清导出、理发师沟通卡与到店团购券服务。生成结果为算法模拟效果，实际剪发效果受发质、发量、理发师操作等影响，仅供决策参考，不构成对效果的承诺或保证。

                    ## 3. 账号与使用规范

                    （1）你应使用本人照片进行预览，不得上传他人照片或侵犯他人肖像权、隐私权的内容；
                    （2）你应提供真实、有效的手机号完成账号绑定，并妥善保管账号；
                    （3）不得利用本服务制作、复制、发布、传播违法违规信息，不得以技术手段批量注册、爬取数据、攻击服务或绕过付费；
                    （4）违反上述规范的，我们有权视情节采取警告、限制功能、暂停或终止服务等措施。

                    ## 4. 付费、退款与发票

                    （1）高清导出、魅力值、会员与团购券为增值服务，价格以下单页面展示为准，金额由服务端计算；
                    （2）因服务自身故障导致未获取到效果图的，可申请退还相应费用；
                    （3）数字内容一经成功交付（高清图已导出或权益已发放）原则上不支持退款，我们会在购买前以显著方式提示；法律法规另有规定或双方另有约定的除外；
                    （4）需要发票的，可通过客服申请，我们将在核实后依法开具。

                    ## 5. 团购券特别约定

                    （1）团购券由平台组织销售，由合作门店提供并核销服务，门店信息在购券页面展示；
                    （2）团购券须在标注的有效期内到指定门店使用，逾期未核销的可按页面或客服指引申请退款；
                    （3）已核销的团购券视为服务已实际提供，不支持退款；因门店停业、迁址等导致无法履约的，由我们协调退款或更换门店；
                    （4）团购券相关售后由我们统一受理并协调门店处理。

                    ## 6. 知识产权

                    你上传的照片归你所有；生成结果图你可用于个人非商业用途，不得用于商业宣传或冒充真人实拍。发型库图片、界面设计、商标与文案等知识产权归我们或权利人所有，未经许可不得使用。

                    ## 7. AI 生成内容标识

                    所有 AI 生成图片均带有「AI 生成」标识，分享或转发时请保留标识，不得删除、篡改或用于误导他人。你不得利用生成内容从事侵权、诈骗或其他违法活动。

                    ## 8. 责任限制

                    （1）AI 生成结果存在不确定性，我们不保证结果完全符合你的预期；
                    （2）因不可抗力、网络故障、第三方服务（微信、支付渠道、AI 服务商等）原因导致服务中断或数据延迟的，我们将尽力协助处理，并在法律允许范围内不承担责任；
                    （3）本条不排除或限制依法应由我们承担的法定责任。

                    ## 9. 协议变更与终止

                    我们可能根据业务与法律要求更新本协议，更新后将在小程序内提示；重大变更将重新取得你的同意。你可以随时停止使用并申请注销账号；我们有权在你严重违约时终止提供服务。

                    ## 10. 通知与送达

                    我们可通过小程序内通知、页面公告、短信或你预留的联系方式向你发送通知，自发送之日视为送达。

                    ## 11. 法律适用与争议解决

                    本协议适用中华人民共和国法律（不含冲突规范）。因本协议产生的争议，双方应友好协商解决；协商不成的，任何一方可向【运营主体所在地】有管辖权的人民法院提起诉讼。

                    ## 12. 未成年人

                    未满 18 周岁的未成年人应在监护人同意和指导下使用本服务；未满 14 周岁的儿童不得单独使用。

                    ## 13. 联系我们

                    客服微信：fajing-ai；邮箱：【support@你的域名】。我们将在 15 个工作日内答复你的咨询与投诉。
                    """);
            setting.setPrivacyContent("""
                    ## 1. 运营者与适用范围

                    本政策由【运营主体全称】（以下简称「我们」）发布并适用。注册地址：【省市区详细地址】；联系邮箱：【privacy@你的域名】；个人信息保护负责人邮箱：【同左】。

                    本政策适用于「发镜 AI」微信小程序及 H5 站点。我们按照《个人信息保护法》《网络安全法》《数据安全法》《人脸识别技术应用安全管理办法》等法律法规处理你的个人信息。

                    ## 2. 我们收集哪些信息及用途

                    （1）登录与账号信息：微信 OpenID/UnionID、手机号、昵称与头像。OpenID 用于识别账号（必需）；手机号用于账号安全、支付与订单通知（充值、购券与售后必需）；昵称、头像用于个人页展示（可选）。
                    （2）人脸照片：你主动上传的正脸照片，是生成换发型效果的核心输入（必需，需单独同意）。
                    （3）使用信息：你选择的发型、发色、生成结果图与试发记录，用于完成生成、保存记录与再次解锁。
                    （4）交易信息：订单号、商户单号、支付渠道交易号、金额、支付与退款状态，用于下单、对账、退款与售后（必需）。
                    （5）设备与日志信息：IP 地址、设备型号、操作系统与微信版本、访问时间、错误日志，用于安全风控、故障排查与反作弊（必需）。
                    （6）客服沟通信息：你与我们沟通时提供的内容，用于处理咨询与投诉。

                    我们不收集与服务无关的信息，不会将上述信息用于本政策未载明的其他目的。

                    ## 3. 人脸照片属于敏感个人信息

                    人脸照片属于《个人信息保护法》规定的敏感个人信息。处理前我们会以显著方式提示并取得你的单独同意（上传页的「人脸信息单独同意」勾选项）。我们仅在提供换发型预览所必需的范围内处理，不建立人脸特征库、不用于模型训练、不用于身份识别，也不对外提供用于其他用途。

                    ## 4. 委托处理与第三方共享

                    为完成服务，我们会在必要范围内委托或接入以下服务商，并要求其仅按我们的指令处理、采取保密与安全措施：

                    （1）AI 图像与文本处理：硅基流动（SiliconFlow）、深度求索（DeepSeek），用于根据照片生成换发效果与造型建议；
                    （2）对象存储：【对象存储服务商，如阿里云 OSS / 腾讯云 COS / 自建 MinIO】；
                    （3）支付服务：财付通支付科技有限公司（微信支付）、支付宝（中国）网络技术有限公司（支付宝）、抖音支付【按实际填写】；
                    （4）微信开放平台：深圳市腾讯计算机系统有限公司，用于微信登录与手机号授权。

                    除上述情形及法律法规要求外，我们不会向第三方提供你的个人信息；如变更服务商，我们将更新本政策并依法重新取得同意。

                    你的个人信息存储于中华人民共和国境内，目前不涉及向境外提供。如未来确需出境，我们将依法单独取得你的同意。

                    ## 5. 保存期限与删除

                    （1）人脸原图：生成结束（含失败）后 24 小时内自动删除；
                    （2）生成结果图与试发记录：保存至你主动删除或注销账号；
                    （3）订单与交易记录：自交易完成之日起保存不少于 3 年（法律法规另有规定的从其规定）；
                    （4）网络日志：保存不少于 6 个月；
                    （5）客服沟通记录：处理完毕后保存不超过 6 个月。

                    超出保存期限或你提出删除请求后，我们将删除或匿名化处理，法律法规要求留存的除外。

                    ## 6. 你的权利

                    你对自己的个人信息享有查阅、复制、更正、补充、删除、撤回同意、注销账号等权利。

                    （1）查阅与删除：可在「我的 → 隐私与数据」查看或一键删除照片与试发记录；
                    （2）撤回同意：可通过本政策第 9 条的联系方式提出，撤回后我们将停止相应处理并删除已上传原图；
                    （3）注销账号：可通过第 9 条联系方式提交注销申请，我们将在 15 个工作日内完成核验并删除或匿名化你的账号信息（交易记录等法定留存信息除外）。

                    我们将在收到请求并核验身份后 15 个工作日内答复。

                    ## 7. 未成年人保护

                    本服务不面向不满 14 周岁的儿童。未满 18 周岁的未成年人应在监护人同意和指导下使用；未满 14 周岁的，须由监护人代为同意本政策并陪同使用。若我们发现未经监护人同意收集了儿童个人信息，将尽快删除。

                    ## 8. 安全措施

                    我们通过 HTTPS 加密传输、访问鉴权与最小权限、操作审计、加密存储与定期备份等措施保护你的信息安全。发生个人信息安全事件时，我们将按法律要求及时告知你并向监管部门报告。我们会依法对处理人脸信息等敏感个人信息开展个人信息保护影响评估并留存记录。

                    ## 9. 联系我们

                    邮箱：【privacy@你的域名】
                    微信客服：fajing-ai（可在「我的 → 联系客服」查看最新方式）

                    我们将在 15 个工作日内答复。如对处理结果不满意，可向网信、市场监管等监管部门投诉举报。

                    ## 10. 政策更新

                    本政策版本 V1.0，生效日期：【填写具体日期】。我们可能适时修订本政策，修订后将在小程序内以显著方式提示；涉及处理目的、方式或信息种类等重大变更的，我们将重新取得你的同意。
                    """);
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
