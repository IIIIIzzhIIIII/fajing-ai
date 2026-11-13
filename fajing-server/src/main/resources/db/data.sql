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
