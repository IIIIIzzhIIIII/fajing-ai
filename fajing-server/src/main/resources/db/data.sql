-- 发镜 AI · 初始化数据（可重复执行）
INSERT IGNORE INTO hairstyle (id, name, gender, cat, len, face, en, img_url, status, sort) VALUES
('f1','法式波波头','female','波波头','肩部以上','圆脸 / 鹅蛋脸','french bob haircut, chin length, soft inward ends','',1,1),
('f2','日系碎短发','female','短发','耳下','小脸 / 心形脸','japanese layered short hair, airy texture','',1,2),
('f3','港风大波浪','female','卷发','胸上','鹅蛋脸 / 方脸','modern soft big wavy long hair, layered volume, glossy finish','',1,3),
('f4','空气感齐刘海','female','齐刘海','肩部','长脸 / 鹅蛋脸','see-through blunt bangs with medium straight hair','',1,4),
('f5','挂耳短发','female','短发','耳上','小脸 / 圆脸','sleek ear-tuck short bob','',1,5),
('f6','中长直发','female','中长发','锁骨','所有脸型','glossy medium length straight hair, collarbone length','',1,6),
('f7','羊毛卷','female','卷发','肩部','圆脸 / 方脸','tight curly wool perm hair with volume','',1,7),
('f8','微分狼尾','female','狼尾','肩部','心形脸 / 鹅蛋脸','shaggy wolf cut with layered texture','',1,8),
('f9','水波纹长发','female','长直发','腰部','所有脸型','long hair with soft water wave texture','',1,9),
('f10','高中生直发','female','长直发','肩下','学生感 / 清纯','long straight natural black hair, student style','',1,10),
('f11','层次锁骨发','female','中长发','锁骨','鹅蛋脸 / 长脸','layered collarbone hair with curtain bangs','',1,11),
('f12','公主切','female','齐刘海','中长','小脸 / 心形脸','hime cut with blunt side locks and straight bangs','',1,12),
('m1','栗子头','male','短发','耳上','圆脸 / 学生感','korean chestnut cut short hair with rounded fringe','',1,13),
('m2','微分碎盖','male','短发','眉上','鹅蛋脸 / 长脸','textured fringe korean men hair with see-through bangs','',1,14),
('m3','韩式纹理烫','male','纹理烫','耳下','所有脸型','korean texture perm men hair, soft natural waves, light airy volume, modern trendy style','',1,15),
('m4','侧推背头','male','背头','短','方脸 / 成熟','modern slick back men hair with low fade, clean tapered sides, natural shine','',1,16),
('m5','法式短寸','male','寸头','极短','硬朗 / 方脸','french crop buzz cut men hair','',1,17),
('m6','狼尾鲻鱼头','male','狼尾','颈后','心形脸 / 潮男','modern mullet men hair, soft layers, shaved sides, trendy korean style','',1,18),
('m7','三七分毛流','male','短发','耳上','长脸 / 鹅蛋脸','modern korean side part men hair, soft natural flow, light airy volume, exposed forehead, tapered sides','',1,19),
('m8','圆寸','male','寸头','极短','所有脸型','round buzz cut men hair, very short','',1,20),
-- 女款补齐至 20 款
('f13','法式羊毛卷','female','卷发','肩部','圆脸 / 方脸','french curly perm medium hair, soft voluminous curls, natural shine','http://localhost:8081/uploads/style-f13.jpg',1,21),
('f14','锁骨微卷','female','中长发','锁骨','鹅蛋脸 / 长脸','collarbone length soft wavy hair with natural inward ends','http://localhost:8081/uploads/style-f14.jpg',1,22),
('f15','一刀切齐肩发','female','波波头','肩部','所有脸型','blunt shoulder length straight hair, one length cut, sleek finish','http://localhost:8081/uploads/style-f15.jpg',1,23),
('f16','碎发鲻鱼头','female','狼尾','颈后','心形脸 / 潮女','shaggy mullet for women, layered soft fringe, textured ends','http://localhost:8081/uploads/style-f16.jpg',1,24),
('f17','利落高马尾','female','扎发','长发','所有脸型','sleek high ponytail, smooth crown, clean hairline','http://localhost:8081/uploads/style-f17.jpg',1,25),
('f18','优雅低盘发','female','盘发','中长','鹅蛋脸 / 方圆脸','elegant low bun updo with soft face framing strands','http://localhost:8081/uploads/style-f18.jpg',1,26),
('f19','韩式气垫烫','female','卷发','胸上','鹅蛋脸 / 长脸','korean cushion perm long hair, soft c-curl volume, glossy finish','http://localhost:8081/uploads/style-f19.jpg',1,27),
('f20','冷棕中长层次','female','中长发','肩下','方脸 / 鹅蛋脸','layered medium hair with cool brown tone, soft face framing layers','http://localhost:8081/uploads/style-f20.jpg',1,28),
-- 男款补齐至 20 款
('m9','前刺飞机头','male','短发','眉上','长脸 / 方脸','spiky textured quiff men hair, short tapered sides','http://localhost:8081/uploads/style-m9.jpg',1,29),
('m10','美式渐变寸头','male','寸头','极短','所有脸型','american fade buzz cut men hair, clean gradient sides','http://localhost:8081/uploads/style-m10.jpg',1,30),
('m11','油头背梳','male','背头','短','方脸 / 成熟','classic pompadour slick back men hair with natural shine','http://localhost:8081/uploads/style-m11.jpg',1,31),
('m12','韩式三七分中长发','male','中长发','耳下','鹅蛋脸 / 长脸','korean medium length side part men hair, soft wavy flow','http://localhost:8081/uploads/style-m12.jpg',1,32),
('m13','日系空气烫','male','纹理烫','耳下','所有脸型','japanese airy perm men hair, textured light waves','http://localhost:8081/uploads/style-m13.jpg',1,33),
('m14','碎盖头','male','短发','眉上','圆脸 / 学生感','textured crop men hair with choppy fringe','http://localhost:8081/uploads/style-m14.jpg',1,34),
('m15','两侧铲青短卷','male','卷发','耳上','心形脸 / 潮男','short curly men hair with shaved sides, korean style','http://localhost:8081/uploads/style-m15.jpg',1,35),
('m16','中分窗帘刘海','male','中长发','眉下','鹅蛋脸 / 心形脸','middle part curtain bangs men hair, soft layered flow','http://localhost:8081/uploads/style-m16.jpg',1,36),
('m17','复古港风背头','male','背头','短','方脸 / 成熟','retro hong kong style slick back men hair','http://localhost:8081/uploads/style-m17.jpg',1,37),
('m18','自然短碎发','male','短发','耳上','所有脸型','natural short textured men hair, low maintenance','http://localhost:8081/uploads/style-m18.jpg',1,38),
('m19','灰白奶奶头','male','短发','耳下','圆脸 / 潮男','silver gray korean grandpa style men hair, soft volume','http://localhost:8081/uploads/style-m19.jpg',1,39),
('m20','韩式锅盖头','male','短发','眉上','学生感 / 圆脸','bowl cut men hair, blunt fringe, korean student style','http://localhost:8081/uploads/style-m20.jpg',1,40);

INSERT IGNORE INTO hair_color (id, name, en, bg, sort) VALUES
('c1','黑茶色','black tea brown','#2B2622',1),
('c2','栗棕色','chestnut brown','#6B4A32',2),
('c3','奶茶棕','milk tea brown','#A8825F',3),
('c4','挂耳染','dark hair with lavender inner highlights','linear-gradient(135deg,#2B2622 52%,#C9A0E8)',4),
('c5','巴黎画染','balayage brown highlights','linear-gradient(135deg,#6B4A32,#E3C39B)',5),
('c6','粉棕','dusty pink brown','#C48A94',6),
('c7','雾霾蓝','hazy blue','#5F748C',7),
('c8','树莓红','raspberry red','#8E3350',8);

INSERT IGNORE INTO user_quota (user_id, free_left, quota_date, share_claimed, ad_used, updated_at)
VALUES (1, 1, CURDATE(), 0, 0, NOW());

INSERT IGNORE INTO app_user (id, nickname, gender, status, register_at, last_active_at)
VALUES (1, '发镜体验用户', 'female', 1, NOW(), NOW());

-- 基础设置默认文案：空行分段，## 开头为小标题
INSERT IGNORE INTO site_setting (id, hero_title_main, hero_title_accent, contact_wechat, contact_phone, about_content, terms_content, privacy_content, face_consent_text, updated_at, updated_by) VALUES
(1, '换个发型', '换张脸', 'fajing-ai', '',
'## 我们是谁

发镜 AI 专注 AI 换发型预览：上传一张正脸照，先看到自己换发型后的样子，再决定要不要剪。

## 我们坚持什么

照片用完即删，不建人脸库，不用于模型训练。生成结果仅供造型参考，实际效果受发质、发量与理发师操作影响。',
'## 1. 服务内容

发镜 AI 提供基于人工智能的发型预览、付费高清导出与理发师沟通卡服务。生成结果为算法模拟效果，实际剪发效果受发质、发量、理发师操作等影响，仅供决策参考。

## 2. 账号与使用规范

你应使用本人照片进行预览，不得上传他人照片或侵犯他人肖像权的内容；不得利用本服务制作、传播违法违规信息。

## 3. 付费与退款

高清无水印导出为增值服务，按页面展示价格付费。因服务自身故障导致未获取到效果图的，可申请退还相应费用；已成功导出高清图的订单不支持退款。

## 4. 知识产权

你上传的照片归你所有；生成结果图你可用于个人用途，不得用于商业宣传或冒充真人实拍。发型库图片版权归发镜 AI 所有。

## 5. AI 生成内容提示

所有 AI 生成图片均带有「AI 生成」标识，分享或转发时请保留标识，不得用于误导他人。

## 6. 协议变更

我们可能根据需要更新本协议，更新后会在应用内提示，继续使用即视为同意更新后的协议。',
'## 1. 我们收集哪些信息

你上传的人脸照片（含面部特征）、你选择的发型与发色、生成结果图。上述信息仅用于提供换发型预览。

## 2. 人脸照片属于敏感个人信息

人脸照片属于《个人信息保护法》规定的敏感个人信息。我们会在你单独勾选同意后才开始处理，且仅用于本次 AI 换发，不建立人脸特征库、不用于模型训练、不提供给第三方做其他用途。

## 3. 保存期限与删除

上传的原图仅在生成期间使用，生成结束 24 小时内由服务端定时任务自动删除；生成结果图保存在你的试发记录中，你可随时在「我的 - 隐私与数据」中一键删除本机数据。

## 4. 委托处理

生成换发效果时，照片会加密传输至 AI 图像处理服务商进行处理，服务商仅按我们的指令处理，不得留存或另作他用。

## 5. 你的权利

你可以随时撤回同意、删除照片与记录、注销账号。撤回同意后，我们将停止处理并删除已上传的原图。

## 6. 未成年人

未满 14 周岁的用户请在监护人陪同下使用，并由监护人代为同意本政策。

## 7. 联系我们

如对个人信息处理有疑问，可通过「我的 - 联系客服」与我们联系。',
'我同意收集并处理我上传的人脸照片（敏感个人信息），仅用于本次 AI 换发预览，不建人脸库、不用于训练，生成结束 24 小时内删除原图。',
NOW(), 0);

-- 充值套餐默认档位：价格与实际到账以本表为准
INSERT IGNORE INTO points_package (id, title, description, badge, biz_type, price, points, days, status, sort) VALUES
(1, '100 魅力值', '约 10 款发型解锁', '', 'points', 10.00, 100, 0, 1, 1),
(2, '200 魅力值', '约 20 款发型解锁', '多买多省', 'points', 20.00, 200, 0, 1, 2),
(3, 'VIP 包月', '30 天内解锁不限量', '推荐', 'vip', 29.00, 0, 30, 1, 3),
(4, 'VVIP 永久', '永久不限量解锁', '', 'vvip', 99.00, 0, 0, 1, 4);

-- ============================================================
-- 演示租户 / 门店 / 券档位 / 员工（第一版本地联调数据）
-- ============================================================
INSERT IGNORE INTO tenant (id, name, contact_name, contact_phone, status) VALUES
(1, '发镜示范商户', '张店长', '13800000001', 1);

INSERT IGNORE INTO store (id, tenant_id, name, brand_name, address, phone, business_hours,
                          daily_capacity, commission_rate, kyc_status, status) VALUES
(1, 1, '发镜示范店', '发镜 AI 示范店', '上海市静安区示例路 88 号', '021-88886666',
 '10:00-21:00', 20, 10.00, 'approved', 1);

-- 门店专属券档位（store_id=1）
INSERT IGNORE INTO coupon_tier (id, store_id, title, pay_amount, value_amount, description, status, sort) VALUES
(101, 1, '50 元抵 69 元', 50.00, 69.00, '剪发通用，到店出示核销码', 1, 1),
(102, 1, '100 元抵 129 元', 100.00, 129.00, '剪发 + 造型组合更划算', 1, 2),
(103, 1, '200 元抵 239 元', 200.00, 239.00, '烫染套餐专享', 1, 3);

-- 演示员工：店长后台可看到邀请码 EMP001，员工用它绑定
INSERT IGNORE INTO staff (id, tenant_id, store_id, user_id, name, phone, role,
                          invite_code, staff_code, daily_limit, status) VALUES
(1, 1, 1, 0, '小林', '13800000002', 'EMPLOYEE', 'EMP001', 'ST001', 20, 1);
