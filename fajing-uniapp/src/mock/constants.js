/**
 * 发镜 AI · 静态数据（迁移自 H5 原型 fajing-ai.html）
 * 正式版由后端 /api/hairstyles、/api/colors、/api/posts 提供
 */

export const IMG = (p, size) =>
  `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=${encodeURIComponent(p)}&image_size=${size || 'portrait_4_3'}`

export const PORTRAIT = (en, gender) =>
  IMG(
    `${gender === 'male' ? 'young asian man' : 'young asian woman'}, ${en}, front facing portrait, shoulders up, natural window light, clean neutral background, photorealistic, 85mm lens, high detail`
  )

export const DEMO_BEFORE = IMG(
  'young asian woman, front facing bare face portrait, hair tied back, no makeup, soft window light, plain light background, photorealistic, 85mm'
)

export const STYLES = {
  female: [
    { id: 'f1', name: '法式波波头', cat: '波波头', len: '肩部以上', face: '圆脸 / 鹅蛋脸', en: 'french bob haircut, chin length, soft inward ends' },
    { id: 'f2', name: '日系碎短发', cat: '短发', len: '耳下', face: '小脸 / 心形脸', en: 'japanese layered short hair, airy texture' },
    { id: 'f3', name: '港风大波浪', cat: '卷发', len: '胸上', face: '鹅蛋脸 / 方脸', en: 'hong kong retro big wavy long hair' },
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
    { id: 'm3', name: '韩式纹理烫', cat: '纹理烫', len: '耳下', face: '所有脸型', en: 'korean texture perm men hair with wavy volume' },
    { id: 'm4', name: '侧推背头', cat: '背头', len: '短', face: '方脸 / 成熟', en: 'side part slick back men hair with faded sides' },
    { id: 'm5', name: '法式短寸', cat: '寸头', len: '极短', face: '硬朗 / 方脸', en: 'french crop buzz cut men hair' },
    { id: 'm6', name: '狼尾鲻鱼头', cat: '狼尾', len: '颈后', face: '心形脸 / 潮男', en: 'mullet wolf cut men hair with shaved sides' },
    { id: 'm7', name: '三七分毛流', cat: '短发', len: '耳上', face: '长脸 / 鹅蛋脸', en: 'side part men hair with natural hair flow' },
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

export const COMM_AI = (en, gender) =>
  IMG(
    `${gender === 'male' ? 'young asian man' : 'young asian woman'}, ${en}, front facing portrait, shoulders up, natural window light, clean neutral background, photorealistic, 85mm lens, high detail`,
    'square'
  )

export const COMM_REAL = (en, gender) =>
  IMG(
    `${gender === 'male' ? 'young asian man' : 'young asian woman'} just got a haircut, ${en}, casual smartphone selfie, indoor bathroom lighting, natural unedited look, real photo, slight grain`,
    'square'
  )

export const COMM_POSTS = [
  {
    id: 'c1', name: '小鹿', ava: '#E28FA6', meta: '杭州 · 剪后第 2 天', badge: 'real',
    styleId: 'f1', gender: 'female', styleName: '法式波波头',
    text: '拿着 AI 图去找托尼，剪完比想象中还服帖！同事都问我在哪家店剪的，圆脸真的可以冲。',
    ai: COMM_AI('french bob haircut, chin length, soft inward ends', 'female'),
    after: COMM_REAL('fresh french bob haircut, chin length, soft inward ends', 'female'),
    score: 96, tags: ['圆脸友好', '显脸小', '好打理'], likes: 128, liked: false,
    comments: [
      { n: '莉莉', t: '2 小时前', x: '求问怎么跟托尼描述长度的！' },
      { n: '小圆', t: '1 小时前', x: '同款脸型，收藏了，周末就去。' }
    ]
  },
  {
    id: 'c2', name: 'Momo', ava: '#B58BC9', meta: '成都 · 剪后第 1 天', badge: 'real',
    styleId: 'f2', gender: 'female', styleName: '日系碎短发',
    text: 'AI 图上是很轻的碎感，剪完层次有点重…算翻车吗？姐妹们帮我看看要不要回去救。',
    ai: COMM_AI('japanese layered short hair, airy texture', 'female'),
    after: COMM_REAL('short layered japanese haircut, slightly heavy ends, airy fringe', 'female'),
    score: 72, tags: ['层次偏重', '求拯救'], likes: 86, liked: false,
    comments: [
      { n: '阿柚', t: '3 小时前', x: '不算翻车，吹干的时候用点蓬松喷雾会好很多。' },
      { n: '托尼老师', t: '2 小时前', x: '发尾再打薄一点就接近了，别自己剪。' },
      { n: 'Momo', t: '1 小时前', x: '谢谢！明天去店里让师傅修一下发尾。' }
    ]
  },
  {
    id: 'c3', name: '阿柚', ava: '#7FA6C9', meta: '上海 · 还在纠结', badge: 'ask',
    styleId: 'f3', gender: 'female', styleName: '港风大波浪',
    text: '想剪这个港风大波浪，但我的发量偏少，怕烫完撑不起来。有没有同发量的姐妹试过？',
    ai: COMM_AI('hong kong retro big wavy long hair', 'female'), after: null, score: null,
    tags: ['发量少', '求建议'], likes: 203, liked: false,
    comments: [
      { n: '卷卷', t: '4 小时前', x: '发量少建议先烫根部，卷度选大一号，会蓬很多。' },
      { n: '小鹿', t: '2 小时前', x: '我发量也少，剪了同款，重点是层次不要太高。' }
    ]
  },
  {
    id: 'c4', name: '理工男阿哲', ava: '#6E8C7A', meta: '深圳 · 剪后第 3 天', badge: 'real',
    styleId: 'm2', gender: 'male', styleName: '微分碎盖',
    text: '第一次拿 AI 效果图去剪，居然没翻车。两侧推了 #2，顶部保留长度，比之前精神多了。',
    ai: COMM_AI('textured fringe korean men hair with see-through bangs', 'male'),
    after: COMM_REAL('fresh textured fringe korean men haircut, faded sides, natural fringe', 'male'),
    score: 93, tags: ['男生可抄', '通勤'], likes: 64, liked: false,
    comments: [{ n: '阿凯', t: '5 小时前', x: '兄弟，理发师沟通卡也发我一份。' }]
  },
  {
    id: 'c5', name: '卷卷', ava: '#C99A7F', meta: '广州 · 剪后第 5 天', badge: 'real',
    styleId: 'f7', gender: 'female', styleName: '羊毛卷',
    text: '羊毛卷真的显发量！就是每天早上要花 10 分钟抓一抓，懒人慎入。',
    ai: COMM_AI('tight curly wool perm hair with volume', 'female'),
    after: COMM_REAL('fresh curly perm hair, bouncy volume, natural texture', 'female'),
    score: 88, tags: ['显发量', '需要打理'], likes: 152, liked: false,
    comments: [{ n: '小鹿', t: '6 小时前', x: '好看！请问是多大的卷？' }]
  }
]

export const COMM_FILTERS = ['全部', '真实作业', '求建议', '女款', '男款', '圆脸友好']

export const COMM_TOPICS = ['# 剪后第 1 天', '# 圆脸友好', '# 发量少', '# 男生可抄', '# 通勤好打理']
