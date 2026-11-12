-- 发镜 AI · 初始化数据（可重复执行）
INSERT IGNORE INTO hairstyle (id, name, gender, cat, len, face, en, img_url, status, sort) VALUES
('f1','法式波波头','female','波波头','肩部以上','圆脸 / 鹅蛋脸','french bob haircut, chin length, soft inward ends','',1,1),
('f2','日系碎短发','female','短发','耳下','小脸 / 心形脸','japanese layered short hair, airy texture','',1,2),
('f3','港风大波浪','female','卷发','胸上','鹅蛋脸 / 方脸','hong kong retro big wavy long hair','',1,3),
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
('m3','韩式纹理烫','male','纹理烫','耳下','所有脸型','korean texture perm men hair with wavy volume','',1,15),
('m4','侧推背头','male','背头','短','方脸 / 成熟','side part slick back men hair with faded sides','',1,16),
('m5','法式短寸','male','寸头','极短','硬朗 / 方脸','french crop buzz cut men hair','',1,17),
('m6','狼尾鲻鱼头','male','狼尾','颈后','心形脸 / 潮男','mullet wolf cut men hair with shaved sides','',1,18),
('m7','三七分毛流','male','短发','耳上','长脸 / 鹅蛋脸','side part men hair with natural hair flow','',1,19),
('m8','圆寸','male','寸头','极短','所有脸型','round buzz cut men hair, very short','',1,20);

INSERT IGNORE INTO hair_color (id, name, en, bg, sort) VALUES
('c1','黑茶色','black tea brown','#2B2622',1),
('c2','栗棕色','chestnut brown','#6B4A32',2),
('c3','奶茶棕','milk tea brown','#A8825F',3),
('c4','挂耳染','dark hair with lavender inner highlights','linear-gradient(135deg,#2B2622 52%,#C9A0E8)',4),
('c5','巴黎画染','balayage brown highlights','linear-gradient(135deg,#6B4A32,#E3C39B)',5),
('c6','粉棕','dusty pink brown','#C48A94',6),
('c7','雾霾蓝','hazy blue','#5F748C',7),
('c8','树莓红','raspberry red','#8E3350',8);

INSERT IGNORE INTO post (id, user_id, name, avatar_color, meta, badge, gender, style_name, content, ai_image, after_image, score, tags, likes, liked, created_at) VALUES
(1,1,'小鹿','#E28FA6','杭州 · 剪后第 2 天','real','female','法式波波头','拿着 AI 图去找托尼，剪完比想象中还服帖！同事都问我在哪家店剪的，圆脸真的可以冲。','https://picsum.photos/seed/fajing_c1_ai/600/600','https://picsum.photos/seed/fajing_c1_real/600/600',96,'圆脸友好,显脸小,好打理',128,0,NOW() - INTERVAL 2 HOUR),
(2,1,'Momo','#B58BC9','成都 · 剪后第 1 天','real','female','日系碎短发','AI 图上是很轻的碎感，剪完层次有点重…算翻车吗？姐妹们帮我看看要不要回去救。','https://picsum.photos/seed/fajing_c2_ai/600/600','https://picsum.photos/seed/fajing_c2_real/600/600',72,'层次偏重,求拯救',86,0,NOW() - INTERVAL 3 HOUR),
(3,1,'阿柚','#7FA6C9','上海 · 还在纠结','ask','female','港风大波浪','想剪这个港风大波浪，但我的发量偏少，怕烫完撑不起来。有没有同发量的姐妹试过？','https://picsum.photos/seed/fajing_c3_ai/600/600','',NULL,'发量少,求建议',203,0,NOW() - INTERVAL 4 HOUR),
(4,1,'理工男阿哲','#6E8C7A','深圳 · 剪后第 3 天','real','male','微分碎盖','第一次拿 AI 效果图去剪，居然没翻车。两侧推了 #2，顶部保留长度，比之前精神多了。','https://picsum.photos/seed/fajing_c4_ai/600/600','https://picsum.photos/seed/fajing_c4_real/600/600',93,'男生可抄,通勤',64,0,NOW() - INTERVAL 5 HOUR),
(5,1,'卷卷','#C99A7F','广州 · 剪后第 5 天','real','female','羊毛卷','羊毛卷真的显发量！就是每天早上要花 10 分钟抓一抓，懒人慎入。','https://picsum.photos/seed/fajing_c5_ai/600/600','https://picsum.photos/seed/fajing_c5_real/600/600',88,'显发量,需要打理',152,0,NOW() - INTERVAL 6 HOUR);

INSERT IGNORE INTO comment (id, post_id, user_name, content, created_at) VALUES
(1,1,'莉莉','求问怎么跟托尼描述长度的！',NOW() - INTERVAL 2 HOUR),
(2,1,'小圆','同款脸型，收藏了，周末就去。',NOW() - INTERVAL 1 HOUR),
(3,2,'阿柚','不算翻车，吹干的时候用点蓬松喷雾会好很多。',NOW() - INTERVAL 3 HOUR),
(4,2,'托尼老师','发尾再打薄一点就接近了，别自己剪。',NOW() - INTERVAL 2 HOUR),
(5,3,'卷卷','发量少建议先烫根部，卷度选大一号，会蓬很多。',NOW() - INTERVAL 4 HOUR),
(6,3,'小鹿','我发量也少，剪了同款，重点是层次不要太高。',NOW() - INTERVAL 2 HOUR),
(7,4,'阿凯','兄弟，理发师沟通卡也发我一份。',NOW() - INTERVAL 5 HOUR),
(8,5,'小鹿','好看！请问是多大的卷？',NOW() - INTERVAL 6 HOUR);

INSERT IGNORE INTO user_quota (user_id, free_left, quota_date, share_claimed, ad_used, updated_at)
VALUES (1, 1, CURDATE(), 0, 0, NOW());

INSERT IGNORE INTO app_user (id, nickname, gender, status, register_at, last_active_at)
VALUES (1, '发镜体验用户', 'female', 1, NOW(), NOW());

-- 发型描述更新：统一为现代流行款式描述（幂等，可重复执行）
UPDATE hairstyle SET en='modern soft big wavy long hair, layered volume, glossy finish' WHERE id='f3';
UPDATE hairstyle SET en='korean texture perm men hair, soft natural waves, light airy volume, modern trendy style' WHERE id='m3';
UPDATE hairstyle SET en='modern slick back men hair with low fade, clean tapered sides, natural shine' WHERE id='m4';
UPDATE hairstyle SET en='modern mullet men hair, soft layers, shaved sides, trendy korean style' WHERE id='m6';
UPDATE hairstyle SET en='modern korean side part men hair, soft natural flow, light airy volume, exposed forehead, tapered sides' WHERE id='m7';

-- 讨论区示例图：换成真实发型示例（AI 效果 / 剪后实拍），不再用随机风景图（幂等）
UPDATE post SET ai_image='http://localhost:8081/uploads/ai_25_f1_1794467034354.png',
                after_image='http://localhost:8081/uploads/80ee4800dde443a19543b2df0ef9f806.jpg'
WHERE id=1;
UPDATE post SET ai_image='http://localhost:8081/uploads/ai_26_f2_1794470130081.png',
                after_image='http://localhost:8081/uploads/095a2690109e438fa5dd19025e6908f1.jpg'
WHERE id=2;
UPDATE post SET ai_image='http://localhost:8081/uploads/02caa3b8f9d84e94beb45b42b0f86482.jpg',
                after_image=''
WHERE id=3;
UPDATE post SET ai_image='http://localhost:8081/uploads/ai_17_m2_1794457873568.png',
                after_image='http://localhost:8081/uploads/91f27d40de73448faa0bf6288a58a884.jpg'
WHERE id=4;
UPDATE post SET ai_image='http://localhost:8081/uploads/ai_26_f7_1794470130191.png',
                after_image='http://localhost:8081/uploads/5b4481a719b34998800762d66fccaa8d.jpg'
WHERE id=5;
