# 发镜 AI · fajing-ai

> 剪之前，先看见。

AI 换发型试镜产品：上传一张正脸照，8 秒生成 4 款新发型效果图，支持前后对比、发色预览、理发师沟通卡、付费解锁高清无水印图与发型讨论区。

视觉与交互以原型 [fajing-ai.html](./fajing-ai/fajing-ai.html) 为准（375px 设计稿，主色 `#C95676`，背景 `#FAF8F7`）。

## 目录结构

| 目录 | 说明 | 端口 |
| --- | --- | --- |
| `fajing-uniapp/` | 前端（Uniapp + Vue3 + Vite + Pinia + SCSS），H5 / 微信小程序 | 5173 |
| `fajing-server/` | 后端（Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL 8） | 8080 |
| `fajing-admin/` | 管理后台（Vue3 + Element Plus + Vite） | 8081 |
| `uploads/` | 用户上传原图（运行期生成，已 gitignore） | - |
| `fajing-ai/` | H5 视觉原型（唯一视觉基准） | - |

## 技术栈

- 前端：Uniapp、Vue 3、Vite、Pinia、SCSS
- 后端：Spring Boot 3.2、Java 17、MyBatis-Plus 3.5.6、MySQL 8、Lombok
- 管理后台：Vue 3、Element Plus、Vite

## 本地开发

环境要求：JDK 17、Maven 3.9+、Node 18+、MySQL 8。

```bash
# 1. 数据库（账号 root / 密码 123456，可在 application.yml 修改）
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS fajing DEFAULT CHARACTER SET utf8mb4;"

# 2. 后端（自动建表 + 写入种子数据）
cd fajing-server
mvn spring-boot:run            # http://localhost:8080

# 3. 前端 H5
cd fajing-uniapp
npm install
npm run dev:h5                 # http://localhost:5173

# 4. 微信小程序
npm run dev:mp-weixin          # 产物在 dist/dev/mp-weixin，用微信开发者工具导入

# 5. 管理后台
cd fajing-admin
npm install
npm run dev                    # http://localhost:8081  admin / admin123
```

## 接口速览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/hairstyles?gender=&cat=` | 发型列表 |
| GET | `/api/colors` | 发色列表 |
| POST | `/api/upload` | 上传照片（jpg/png/webp，≤10MB） |
| POST | `/api/generate` | 提交生成任务（异步 8 秒） |
| GET | `/api/generate/{taskId}` | 轮询生成进度与结果 |
| GET | `/api/records` | 试发历史 |
| GET | `/api/quota` | 今日额度 |
| POST | `/api/quota/consume` | 消耗额度（free/share/ad） |
| GET | `/api/posts?filter=` | 讨论区列表 |
| POST | `/api/posts` | 发帖 |
| POST | `/api/posts/{id}/like` | 点赞 |
| GET/POST | `/api/posts/{id}/comments` | 评论列表 / 发表评论 |
| POST | `/api/order/mock` | 模拟支付 |
| GET | `/api/unlocks` | 已解锁发型 |
| POST | `/api/share/reward` | 分享奖励（每账号限 1 次） |
| * | `/api/admin/**` | 管理端接口，需 header `X-Admin-Token: admin-token` |

统一返回体：`{ "code": 0, "msg": "ok", "data": ... }`，`code != 0` 表示业务错误。

## 本阶段故意未做（TODO）

- 微信登录：Controller 内写死 `userId = 1L`
- 真实 AI 出图：`@Async` 等待 8 秒后返回 picsum 占位图
- 真实支付：`/api/order/mock` 直接返回 `{paid:true}`
- 管理端权限：仅校验 `X-Admin-Token`，账号 `admin/admin123` 为前端固定账号

## 相关文档

- [DECISIONS.md](./DECISIONS.md)：技术选型与实现取舍记录
