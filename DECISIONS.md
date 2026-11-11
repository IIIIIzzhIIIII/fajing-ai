# DECISIONS · 技术决策记录

本文件记录开发过程中的技术选择、取舍与遗留项。

## 1. 前端尺寸单位：px（而非全量 rpx）

原型是固定 375px 设计稿，且桌面端以 420px 手机壳居中预览（左右有品牌/流程装饰）。
若全量改为 rpx，桌面端会随视口放大到不可控的宽度，破坏手机壳预览效果。
因此统一使用 px，并用 `#app { max-width: 420px }` 保持形态；后续如需纯插件化微信端适配，可在 `styles/base.scss` 增加媒体查询微调。
设计变量（颜色、圆角、阴影）同时以 CSS 变量与 SCSS 变量两种形式提供。

## 2. tabBar：自绘组件而非原生 tabBar

原型 tabBar 位于手机壳内部底部，原生 tabBar 在 H5 桌面端会横跨整个视口，破坏预览。
因此用 `components/FjTabBar.vue` 自绘三项（首页 / 讨论 / 我的），页面切换用 `uni.reLaunch`。
`pages.json` 中不再声明原生 tabBar，避免双重底栏。

## 3. 图标：SVG → base64 背景图

原型使用内联 SVG 精灵（`<use href="#i-xxx">`），小程序不支持 SVG 标签。
`components/FjIcon.vue` 将图标路径拼成完整 SVG，经 UTF-8 转 base64 后作为 `background-image`，H5 与微信小程序表现一致，且支持自定义颜色与尺寸。

## 4. 全局弹层集中在 FjSheet

toast、遮罩、额度、付费、支付成功、分享、发布、评论、隐私共用一个全局组件，状态由 `store/ui.js` 驱动。
好处：9 个页面不重复实现弹层；页面之间只需 `ui.openSheet('pay')`。
跨页面数据传递（打开评论、发布成功刷新）通过 `uni.$emit/$on` 完成。

## 5. 后端异步生成

`POST /api/generate` 同步插入任务后立即返回 taskId，真正的出图由 `GenerationProcessor` 的 `@Async` 方法执行（8 秒，进度 15/40/65/90）。
拆成独立 Bean 是为了避免 `@Async` 自调用的代理失效问题。
阶段后期接入真实 AI 时，只需替换 `finish()` 中的占位图逻辑。

## 6. 额度与分享奖励

- 每日免费 1 次，按 `user_quota.quota_date` 判断是否跨天重置。
- 分享奖励每账号终身 1 次，`share_claimed` 不随每日重置；前端同步置灰按钮并改文案。
- 看广告每次加 1 次，每日重置。
- 清除本机数据时前端一并重置 share_claimed 标记，后端保留数据库记录（真实上线应由服务端校验）。

## 7. 解锁与水印

生成完成后第 1 款默认解锁（免费），其余需付费或分享解锁。
未解锁图在保存时由 H5 Canvas 叠加右下角 "AI 生成 · 发镜 AI" 水印；已解锁则原图保存。
小程序端 `saveImageToPhotosAlbum` 不支持服务层 Canvas 合成，当前为直接保存，水印合成留待后续用页面级 canvas 实现。

## 8. 数据库初始化

使用 `spring.sql.init`（`schema.sql` + `data.sql`，`continue-on-error=true`），建表与种子数据全部幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`），重复启动不会产生重复数据。
未引入 Flyway，避免阶段早期的迁移管理成本。

## 9. 管理端鉴权

`/api/admin/**` 由 `AdminInterceptor` 校验 `X-Admin-Token: admin-token`。
前端管理后台固定账号 `admin/admin123`，仅做路由守卫，不构成真实安全边界（阶段约定，后续接 RuoYi 权限）。

## 10. 遗留 TODO

- 微信登录（JWT）替换写死的 `userId = 1L`
- 接入真实 AI 出图服务
- 接入微信支付与订单对账
- 小程序端水印合成
- 上传文件接入对象存储（当前落本机 `uploads/`）
