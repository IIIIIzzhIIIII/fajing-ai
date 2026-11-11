# 发镜 AI · 全栈开发实施方案

> 盘点日期：2026-11-11 ｜ 依据：项目内 4 份现有资产 + 微信/云厂商现行规则
> 目标读者：一个人（非团队）把原型做成能上线的真产品

---

## 0. 现状盘点：你手里到底有什么

| 资产 | 实际内容 | 能不能直接变产品 |
| --- | --- | --- |
| `AI换发型H5产品规划与原型提示词.md` | P0/P1/P2 功能清单、竞品卖点拆解、标准 SOP、合规待办 | ✅ 当需求基线，不用重写 |
| `fajing-ai/fajing-ai.html`（172k 字符） | 9 个页面：启动/首页/上传/选款/生成中/结果/沟通卡/我的/讨论；含额度、付费墙、分享、发帖、评论全交互 | ⚠️ 只当 UI 设计稿 + 交互基准 |
| `fajing-ai-sop/fajing-ai-sop.html` | 页面操作 SOP、业务规则、埋点、验收清单、合规门槛 | ✅ 直接当验收标准 |
| `.trae-html-share-packages`、`.screenshots` | 分享包 zip、截图 | 无影响，可忽略 |

### 原型里三处"假"，就是你要补的三条真链路

1. **结果图是假的**：`fajing-ai.html:1497` 的 `IMG()` 调的是一个**文生图接口**，按 prompt「young asian woman, french bob haircut…」生成一张**陌生人**的照片。**跟你上传的那张脸没有任何关系**。所以"上传自己的照片试发型"这个核心卖点，当前完成度是 0。
2. **状态是假的**：额度、历史记录、点赞、评论、帖子，全在 `localStorage`（原型 1542、1669、1762 行）。换台设备就没了，也无法做风控。
3. **钱是假的**：付费弹窗只 toast 一句"原型演示"。

**结论：策划 100%，UI 原型 100%，真实后端 0%。** 你接下来 80% 的工作量不在界面，而在下面四件事：真上传、真出图、真额度、真合规。

---

## 1. 三个前置决策（先定，再写代码）

### 1.1 载体与主体 —— 唯一的"做完了也救不回来"的坑

- 微信小程序 **「深度合成」类目**（AI 换脸 / AI 绘画 / AI 问答）**不对个人主体开放**，必须企业或个体户主体；并且要提供《互联网信息服务算法备案》或第三方合作协议（含算法名称、应用场景、备案编号）。
- 生成内容必须显著标注"AI 生成"。
- 小程序本身还要 ICP 备案（域名 + 小程序备案），域名必须 HTTPS + 已备案，才能配 request/downloadFile 合法域名。
- 你自己的规划文档里已经写了这条待办（"要对方提供算法备案编号用于微信类目申请"），说明方向是对的，只是**这件事必须和写代码并行，不能等写完再做**。

**推荐路径**：先用 H5 内测（自己的域名 + ICP 备案）跑通技术链路 → 同时办个体工商户执照（重庆可在政务服务网/渝快办线上提交，成本低）→ 用个体户主体申请小程序 + 深度合成类目 + 算法备案 → 提审上架。

### 1.2 付费 —— 别用普通微信支付卖"解锁"

- 小程序内**虚拟商品**（解锁高清、会员、次数包）**必须走「小程序虚拟支付」**，不允许引导到 H5 / 公众号 / 外部收钱。
- **iOS 端不允许展示任何虚拟商品购买入口**（要走 Apple 支付通道需开通虚拟支付 + 配置小程序简称 + 微信客户端 ≥ 8.0.68，条件多、审核严）。

**推荐**：MVP **不做真钱**。先做"免费 1 款 + 分享解锁 1 款 + 激励视频广告"，把留存和复购数据跑出来；付费只开 Android 灰度，或整个挪到 H5 端。这样能砍掉一周的开发量和一半的审核风险。

**顺带修正一个产品错误**：原型文案是"解锁高清**无水印**"（`fajing-ai.html:1288`）。深度合成规定要求生成内容保留"AI 生成"标识，**不能付费去掉**。付费权益应改成「高清导出 / 去产品 Logo / 去噪点修复」，AI 角标保留。

### 1.3 AI 能力 —— 单位成本决定你能不能"每天免费送 4 张"

| 候选引擎 | 技术打法 | 参考单价 | 主要风险 |
| --- | --- | --- | --- |
| 火山方舟 **Seedream 5.0 Pro** | 多图参考（最多 10 张）：图1 是本人，图2 是发型参考，要求"保持人脸不动，只换发型" | 按张计费，2K 档更高 | 一致性依赖 prompt 和参考图质量，可能连脸一起改 |
| 火山方舟 **SeedEdit 3.0** | 指令编辑："保持五官脸型不变，只把发型改成…" | **0.30 元/张**，新用户 500 次体验额度 | 发型改动幅度大时容易"换脸感" |
| 腾讯云 **人脸融合 / 人像变换** | 把用户人脸融合进"发型素材图" | 按次计费 | 丢失姿态和身体，只适合头肩照 |

**成本红线**：0.30 元/张 × 4 张 = **1.2 元/次生成**。你 SOP 里写的是"每日 1 次免费生成（可选 4 款）"——1000 个日活就是 **1200 元/天**纯成本，这个模型跑不起来。

**必须改的规则**：免费 = 1 款；4 款对比 = 付费 / 分享 / 看广告的权益。

**先做评测再选型**：拿你自己的 20 张真实自拍 × 3 个引擎 × 4 款发型，按「像不像我（人脸相似度）／发型像不像／有无畸形（发丝断层、耳朵消失）」三项打分。半天到一天就能定主引擎和备引擎，别信官方 demo。

---

## 2. 技术选型（对着你现有的技能栈）

| 层 | 选型 | 为什么 |
| --- | --- | --- |
| 前端 | **uni-app（Vue3 + TS + scss）**，一套代码出微信小程序 + H5 | 你的原型是单文件 HTML，小程序**不支持 Tailwind CDN、不支持 hash 路由、主包上限 2MB**，不能直接搬。原型的 CSS 变量（`--pink` / `--r-lg` 等）可以 1:1 翻成 scss |
| 后端 | **Spring Boot 3.x + JDK 17 + MyBatis-Plus + MySQL 8 + Redis 7** | 你已经在学 Spring Boot，直接上 |
| 图片存储 | 阿里云 OSS / 腾讯云 COS，**客户端 STS 临时凭证直传** | 原图 3-5MB，走服务器中转既慢又贵 |
| 异步任务 | 「提交任务 → 轮询状态」，状态放 Redis | 生成 10-30s，小程序端不能长连接死等；轮询实现最简单 |
| AI 层 | 抽象 `HairEngine` 接口 + 多实现（Provider 模式），配置切换 | 换供应商时不动业务代码，方便比价 |
| 微信侧 | `wx.login` + `code2session`、保存相册、转发、内容安全 `msgSecCheck`/`imgSecCheck` | 讨论区必须接内容审核，否则必封 |
| 部署 | 一台 2C4G 云服务器 + Docker Compose（nginx / app / mysql / redis） | 前期够用，月成本百元级 |

---

## 3. 开发顺序（先能跑 → 再能卖 → 最后能合规）

| 阶段 | 目标 | 具体任务 | 验收标准 | 预估 |
| --- | --- | --- | --- | --- |
| **P0 引擎验证** | 确认"真能换出像自己的发型" | 注册火山方舟 → 写一个 50 行的 Python/Java 脚本，批量跑 SeedEdit 3.0 和 Seedream 5.0 参考图模式；产出 20×3×4 的结果墙 + 打分表 | 至少一个引擎在 70% 的照片上"脸没变、发型对了" | 2 天 |
| **P1 后端骨架** | 照片能真传上来 | Spring Boot 工程、MySQL 建表、OSS STS 直传、`/faces/check`（正脸/清晰度校验）、小程序登录 | 真机上传一张自拍，OSS 里能看到对象；侧脸/模糊照片被拦下 | 5 天 |
| **P2 生成闭环** ⭐ | 全链路真出图 | 任务表 + Redis 状态 + 异步 worker 调 AI + 轮询接口 + 结果落库 + 24h 清理定时任务 | 真机：选 4 款 → 首图 < 15s 出来 → 滑动对比 → 4 张都能切 | 10 天（**项目生死线**） |
| **P3 额度与登录** | 次数说得清、扣得准 | 微信登录发 JWT、每日额度、分享/广告奖励、**预扣 + 失败回滚**、幂等键 | 生成失败不扣次数；同一任务重复提交只扣一次 | 5 天 |
| **P4 结果页与保存** | 核心爽点做顺 | 对比滑杆（`movable-area` 或 canvas 裁剪）、保存相册（授权 + 域名白名单）、海报生成（canvas 2d 离屏绘制）、AI 标识水印 | 保存到相册成功、海报带品牌与 AI 角标、滑杆拖动不卡 | 7 天 |
| **P5 我的与历史** | 用户有"资产感" | 历史列表、回到历史结果、清空记录、隐私说明页 | 换设备登录后历史还在 | 4 天 |
| **P6 讨论区** | 留存与信任 | 发帖（AI 图 + 剪后实拍）、点赞、评论、标签、**内容安全审核** | 违规图/违规词被拦，帖子可举报 | 7 天（可延后到第二版） |
| **P7 上架** | 过审 | 个体户主体 + 小程序备案 + 深度合成类目 + 算法备案协议 + 隐私政策 + 提审灰度 | 审核通过、真机走一遍验收清单 | 2-4 周（与开发并行） |

**单人（每天 3-4 小时）总周期：6-8 周**，前提是 P0 的引擎评测一次过关。如果 P0 发现三个引擎都"换脸"，要退回做「局部重绘」（先用人脸关键点圈出头发区域，只对该区域做 inpainting），那要再加 1-2 周——**所以 P0 必须先做，别先写 UI**。

---

## 4. 关键接口契约（照着写，别自己发明）

```
POST /api/v1/auth/wx-login        { code }                        -> { token, userId, isNew }
POST /api/v1/uploads/sts          { scene:"photo"|"result" }       -> { objectKey, credentials, expireAt }
POST /api/v1/faces/check          { objectKey }                    -> { ok, reason, faceShape? }
GET  /api/v1/styles               ?gender=female&cat=波波头        -> { list:[{id,name,cat,len,face,coverUrl}] }
GET  /api/v1/colors                                                -> { list:[{id,name,hex}] }
POST /api/v1/generations          { photoKey, styleIds[1..4], colorId?, mode:"hair"|"hair+color" }
                                                                   -> { taskId, quotaLeft, freeCount }
GET  /api/v1/generations/{taskId}                                  -> { status:"QUEUED|RUNNING|DONE|FAILED",
                                                                        progress:0-100,
                                                                        results:[{styleId,name,url,thumbUrl,watermarkUrl}],
                                                                        failReason? }
GET  /api/v1/me/quota                                              -> { freeLeft, adLeft, shareRewarded }
POST /api/v1/me/share-reward                                       -> { ok, freeLeft }
GET  /api/v1/me/history           ?page=1                          -> { list:[{id,title,coverUrl,createdAt,resultIds}] }
DELETE /api/v1/me/history                                          -> { ok }
```

约定：统一 `{ code, msg, data }`；`code=0` 成功；图片全部返回**完整 URL**，前端不做拼接。

---

## 5. 数据库最小表集

| 表 | 关键字段 |
| --- | --- |
| `user` | id, openid(uniq), unionid, nickname, avatar, created_at |
| `quota_daily` | user_id, day(date), free_total, free_used, ad_used, share_rewarded(0/1), uniq(user_id,day) |
| `gen_task` | id, user_id, photo_key, style_ids(json), color_id, mode, status, progress, engine, cost_yuan, idempotency_key(uniq), created_at, finished_at |
| `gen_result` | id, task_id, style_id, name, oss_key, thumb_key, watermark_key, width, height, expire_at |
| `hair_style` | id, gender, name, cat, len, face, cover_key, prompt_en, enabled, sort |
| `hair_color` | id, name, hex, prompt_en, enabled |
| `post` | id, user_id, type(complete/ask), ai_img_key, after_img_key, tags(json), style_name, content, score, status(pending/pass/reject), created_at |
| `comment` / `post_like` | id, post_id, user_id, content, created_at |
| `order`（若做付费） | id, user_id, sku, amount, out_trade_no(uniq), status, paid_at, channel |

索引重点：`gen_task(user_id, created_at)`、`gen_task(idempotency_key)`、`quota_daily(user_id, day)`、`post(status, created_at)`。

---

## 6. 六个一定会踩的坑

1. **图片过服务器**：3-5MB 原图走中转，一台 2C4G 扛不住 20 个并发。→ 客户端直传 OSS，服务端只存 key。
2. **重复提交 / 超时重试**：用户手抖连点两次 = 扣两次次数、跑两个任务。→ 幂等键（`userId + photoKey + styleIds` 的 hash）+ 任务去重。
3. **扣次数与失败回滚**：先扣后失败不退，用户必投诉。→ **冻结（reserve）→ 成功结算 → 失败释放**，每天 00:00 对账。
4. **域名白名单 / 相册授权**：小程序 `downloadFile` 必须配合法域名；`saveImageToPhotosAlbum` 用户拒绝过一次就不再弹。→ 上线前逐项真机验，拒绝时引导到设置页。
5. **"24h 删照片"和历史记录冲突**：原图删了，历史里的结果图还能不能看？→ 明确口径并写进隐私政策。建议：**原图 24h 必删；结果图保留 30 天；用户可一键清空**。SOP 里"照片仅用于本次渲染"指的是原图，别混。
6. **"分享即得次数"无法验证**：微信不给分享成功回调（`onShareAppMessage` 只代表用户点了转发）。→ 服务端记账 + 每账号限 1 次 + 频控，接受一定刷量损耗，别指望精准。

---

## 7. 今天就能动手的三件事

1. **跑引擎评测**：注册火山方舟（领 500 次 SeedEdit 3.0 体验额度），拿你自己的自拍 + 20 张发型参考图，各跑 4 张，做打分表。这一步决定项目能不能成，优先级高于任何代码。
2. **建两个仓库**：`fajing-ai-server`（Spring Boot）、`fajing-ai-app`（uni-app）；把原型 9 个页面做成「页面 → 需要哪些接口 → 需要哪些字段」的对照表，直接对着第 4 节抄。
3. **启动合规**：确认主体类型 → 办个体工商户 → 买域名 + 提 ICP 备案（7-20 天，越早越省事）。同时联系火山引擎要**算法备案编号 + 合作协议**（类目申请要用）。

---

## 8. 需要你拍板的三件事

| 决策 | 选项 | 我的建议 |
| --- | --- | --- |
| 载体 | 微信小程序 / 先 H5 / 双端 | **H5 先跑通 + 小程序并行备案**，代码用 uni-app 一套出两端 |
| 主体 | 个人 / 个体户 / 公司 | **个体户**（个人主体拿不到深度合成类目） |
| 免费额度 | 每天 1 款 / 每天 4 款 | **每天 1 款**（4 款 = 每人每天 1.2 元成本） |

这三条定了，剩下的就是按第 3 节顺序推。
