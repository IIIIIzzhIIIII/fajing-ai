/**
 * 发镜 AI · 静态数据（迁移自 H5 原型 fajing-ai.html）
 * 正式版由后端 /api/hairstyles、/api/colors 提供
 */

export const IMG = (p, size) =>
  `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(p)}&image_size=${size || 'portrait_4_3'}`

export const PORTRAIT = (en, gender) =>
  IMG(
    `${gender === 'male' ? 'young asian man' : 'young asian woman'}, ${en}, ` +
    'front facing portrait, looking straight at camera, head level and centered, symmetrical frontal face, ' +
    'no side profile, no tilted head, shoulders up, natural window light, clean neutral background, ' +
    'photorealistic, 85mm lens, high detail'
  )

export const DEMO_BEFORE = '/static/demo/before.jpg'

/** 本地示范图：打包进小程序，避免远程占位图加载慢或被域名规则拦截 */
export const LOCAL_STYLE_IMG = {
  f1: '/static/styles/f1.jpg',
  f2: '/static/styles/f2.jpg',
  f3: '/static/styles/f3.jpg',
  f4: '/static/styles/f4.jpg',
  f5: '/static/styles/f5.jpg',
  f6: '/static/styles/f6.jpg',
  f7: '/static/styles/f3.jpg',
  f8: '/static/styles/f2.jpg',
  f9: '/static/styles/f6.jpg',
  f10: '/static/styles/f6.jpg',
  f11: '/static/styles/f4.jpg',
  f12: '/static/styles/f4.jpg',
  m1: '/static/styles/m1.jpg',
  m2: '/static/styles/m2.jpg',
  m3: '/static/styles/m3.jpg',
  m4: '/static/styles/m4.jpg',
  m5: '/static/styles/m5.jpg',
  m6: '/static/styles/m6.jpg',
  m7: '/static/styles/m2.jpg',
  m8: '/static/styles/m5.jpg'
}

/** 发型展示图：优先后台/本地图，都没有才回退远程生成 */
export const STYLE_IMG = (s) => (s && LOCAL_STYLE_IMG[s.id]) || ''

export const STYLES = {
  female: [
    { id: 'f1', name: '法式波波头', cat: '波波头', len: '肩部以上', face: '圆脸 / 鹅蛋脸', en: 'french bob haircut, chin length, soft inward ends' },
    { id: 'f2', name: '日系碎短发', cat: '短发', len: '耳下', face: '小脸 / 心形脸', en: 'japanese layered short hair, airy texture' },
    { id: 'f3', name: '港风大波浪', cat: '卷发', len: '胸上', face: '鹅蛋脸 / 方脸', en: 'modern soft big wavy long hair, layered volume, glossy finish' },
    { id: 'f4', name: '空气感齐刘海', cat: '齐刘海', len: '肩部', face: '长脸 / 鹅蛋脸', en: 'see-through blunt bangs with medium straight hair' },
    { id: 'f5', name: '挂耳短发', cat: '短发', len: '耳上', face: '小脸 / 圆脸', en: 'sleek ear-tuck short bob' },
    { id: 'f6', name: '中长直发', cat: '中长发', len: '锁骨', face: '所有脸型', en: 'glossy medium length straight hair, collarbone length' },
    { id: 'f7', name: '羊毛卷', cat: '卷发', len: '肩部', face: '圆脸 / 方脸', en: 'tight curly wool perm hair with volume' },
    { id: 'f8', name: '微分狼尾', cat: '狼尾', len: '肩部', face: '心形脸 / 鹅蛋脸', en: 'shaggy wolf cut with layered texture' },
    { id: 'f9', name: '水波纹长发', cat: '长直发', len: '腰部', face: '所有脸型', en: 'long hair with soft water wave texture' },
    { id: 'f10', name: '高中生直发', cat: '长直发', len: '肩下', face: '学生感 / 清纯', en: 'long straight natural black hair, student style' },
    { id: 'f11', name: '层次锁骨发', cat: '中长发', len: '锁骨', face: '鹅蛋脸 / 长脸', en: 'layered collarbone hair with curtain bangs' },
    { id: 'f12', name: '公主切', cat: '齐刘海', len: '中长', face: '小脸 / 心形脸', en: 'hime cut with blunt side locks and straight bangs' }
  ],
  male: [
    { id: 'm1', name: '栗子头', cat: '短发', len: '耳上', face: '圆脸 / 学生感', en: 'korean chestnut cut short hair with rounded fringe' },
    { id: 'm2', name: '微分碎盖', cat: '短发', len: '眉上', face: '鹅蛋脸 / 长脸', en: 'textured fringe korean men hair with see-through bangs' },
    { id: 'm3', name: '韩式纹理烫', cat: '纹理烫', len: '耳下', face: '所有脸型', en: 'korean texture perm men hair, soft natural waves, light airy volume, modern trendy style' },
    { id: 'm4', name: '侧推背头', cat: '背头', len: '短', face: '方脸 / 成熟', en: 'modern slick back men hair with low fade, clean tapered sides, natural shine' },
    { id: 'm5', name: '法式短寸', cat: '寸头', len: '极短', face: '硬朗 / 方脸', en: 'french crop buzz cut men hair' },
    { id: 'm6', name: '狼尾鲻鱼头', cat: '狼尾', len: '颈后', face: '心形脸 / 潮男', en: 'modern mullet men hair, soft layers, shaved sides, trendy korean style' },
    { id: 'm7', name: '三七分毛流', cat: '短发', len: '耳上', face: '长脸 / 鹅蛋脸', en: 'modern korean side part men hair, soft natural flow, light airy volume, exposed forehead, tapered sides' },
    { id: 'm8', name: '圆寸', cat: '寸头', len: '极短', face: '所有脸型', en: 'round buzz cut men hair, very short' }
  ]
}

export const CATS = {
  female: ['全部', '长直发', '中长发', '波波头', '短发', '卷发', '狼尾', '齐刘海'],
  male: ['全部', '短发', '寸头', '纹理烫', '背头', '狼尾']
}

export const COLORS = [
  { id: 'c1', name: '黑茶色', en: 'black tea brown', bg: '#2B2622' },
  { id: 'c2', name: '栗棕色', en: 'chestnut brown', bg: '#6B4A32' },
  { id: 'c3', name: '奶茶棕', en: 'milk tea brown', bg: '#A8825F' },
  { id: 'c4', name: '挂耳染', en: 'dark hair with lavender inner highlights', bg: 'linear-gradient(135deg,#2B2622 52%,#C9A0E8)' },
  { id: 'c5', name: '巴黎画染', en: 'balayage brown highlights', bg: 'linear-gradient(135deg,#6B4A32,#E3C39B)' },
  { id: 'c6', name: '粉棕', en: 'dusty pink brown', bg: '#C48A94' },
  { id: 'c7', name: '雾霾蓝', en: 'hazy blue', bg: '#5F748C' },
  { id: 'c8', name: '树莓红', en: 'raspberry red', bg: '#8E3350' }
]

export const BARBER_SPEC = {
  female: {
    长直发: { len: '30-35 cm', layer: '低层次', bangs: '无刘海 / 可选八字刘海', side: '两侧自然修剪，不推剪', tip: '发尾平剪，保留厚度，不要打薄' },
    中长发: { len: '锁骨位置（约 25 cm）', layer: '中层次', bangs: '八字刘海', side: '耳前留长，不推剪', tip: '两侧留长修饰脸型，发尾微微内扣' },
    波波头: { len: '下巴到肩之间（约 18 cm）', layer: '齐厚，发尾内扣', bangs: '可配空气刘海', side: '不推剪，鬓角留 2 cm', tip: '不要打薄，保留发尾重量感才服帖' },
    短发: { len: '耳下 3-5 cm', layer: '高层次', bangs: '碎刘海', side: '鬓角留 2 cm，不推剪', tip: '发根要蓬松，别贴头皮剪太薄' },
    卷发: { len: '肩部（约 22 cm）', layer: '中层次', bangs: '无刘海 / 侧分', side: '不推剪', tip: '烫卷直径 2 cm，卷度自然，顶部加高' },
    狼尾: { len: '后颈 8-10 cm，前侧齐下颌', layer: '超高层次', bangs: '碎刘海', side: '两侧保留长度，不推剪', tip: '头顶蓬松，发尾打薄做出飘逸感' },
    齐刘海: { len: '肩部（约 20 cm）', layer: '齐厚', bangs: '齐眉刘海，厚薄均匀', side: '不推剪', tip: '刘海不要短于眉毛，两侧做公主切过渡' }
  },
  male: {
    短发: { len: '顶部 4-5 cm', layer: '中层次', bangs: '自然碎刘海', side: '两侧推剪 #2（6 mm）+ 渐变', tip: '顶部留够长度方便抓造型，发际线修干净' },
    寸头: { len: '全头 #3（10 mm）', layer: '无层次', bangs: '短碎前刺', side: '两侧 #2（6 mm）渐变', tip: '头顶比两侧略长，发际线保留自然形态' },
    纹理烫: { len: '顶部 6-7 cm', layer: '纹理烫 2 号杠', bangs: '微分刘海', side: '两侧 #3（10 mm）', tip: '保持自然流向，不要烫成小卷' },
    背头: { len: '顶部 7-8 cm', layer: '中层次', bangs: '后梳，不留刘海', side: '两侧 #1（3 mm）渐变', tip: '侧分线清晰，后颈收紧，用发蜡定型' },
    狼尾: { len: '后颈 6 cm，前额齐眉', layer: '高层次', bangs: '碎刘海', side: '两侧 #2（6 mm）推剪', tip: '后侧留长不要削薄，前额做轻盈碎感' }
  }
}

export const BARBER_FALLBACK = {
  len: '按参考图比例修剪',
  layer: '自然层次',
  bangs: '按参考图保留',
  side: '两侧自然过渡',
  tip: '以参考图为准，整体保持蓬松自然'
}
