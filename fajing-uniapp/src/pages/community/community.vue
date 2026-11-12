<template>
  <view class="page active">
    <view class="comm-head">
      <view class="comm-head-t">
        <view class="comm-title">
          <text>发型讨论</text>
          <text class="post-badge">真实作业</text>
        </view>
        <view class="comm-sub">AI 预览 vs 剪后实拍 · 少踩一次雷</view>
      </view>
      <view class="comm-post-btn" @click="openCompose('real')">
        <fj-icon name="message" :size="16" color="#FFFFFF" />发讨论
      </view>
    </view>

    <scroll-view scroll-y class="scroll" style="padding-bottom:6px">
      <view class="comm-hero">
        <view class="ch-eyebrow">REAL RESULTS WALL</view>
        <view class="ch-num">2,847<text class="ch-num-unit">份剪后作业</text></view>
        <view class="ch-sub">平均还原度 91% · 今日新增 126 份</view>
        <view class="ch-foot">
          <view class="ch-avs">
            <text class="ch-av" style="background:#E28FA6">鹿</text>
            <text class="ch-av" style="background:#B58BC9">Mo</text>
            <text class="ch-av" style="background:#7FA6C9">柚</text>
          </view>
          <view class="ch-note">真实作业 · 已授权</view>
          <view class="ch-cta ghost" @click="openCompose('ask')">还没剪？</view>
          <view class="ch-cta" @click="openCompose('real')">交作业 →</view>
        </view>
      </view>

      <scroll-view scroll-x class="comm-filters">
        <view class="chip-row">
          <view
            v-for="f in filters"
            :key="f"
            class="cf"
            :class="{ on: activeFilter === f }"
            @click="setFilter(f)"
          >
            {{ f }}<text class="cf-n">{{ filterCount(f) }}</text>
          </view>
        </view>
      </scroll-view>

      <scroll-view scroll-x class="comm-topics">
        <view class="topic-row">
          <text class="ct-label">话题</text>
          <view v-for="t in topics" :key="t" class="ct" @click="pickTopic(t)">{{ t }}</view>
        </view>
      </scroll-view>

      <view v-if="!visiblePosts.length" class="comm-empty">
        这个分类还没有作业<br />
        <view class="empty-link" @click="resetFilters">查看全部作业 →</view>
      </view>

      <view v-for="p in visiblePosts" :key="p.id" class="post">
        <view class="post-head">
          <view class="post-ava" :style="{ background: p.ava }">{{ p.name.slice(0, 1) }}</view>
          <view class="post-wh">
            <view class="post-name">
              <text>{{ p.name }}</text>
              <text class="post-badge" :class="{ ask: p.badge !== 'real' }">
                {{ p.badge === 'real' ? '剪后实拍' : '还没剪' }}
              </text>
              <text v-if="p.aiImage" class="ai-mark">AI 生成</text>
            </view>
            <view class="post-meta">{{ metaLine(p) }}</view>
          </view>
        </view>

        <view class="post-body">{{ p.content }}</view>

        <view v-if="p.badge === 'real' && p.aiImage && p.afterImage" class="post-pair">
          <view class="pair-cell before">
            <image :src="p.aiImage" mode="aspectFill" class="pair-img" />
            <text class="pair-tag">AI 预览</text>
          </view>
          <view class="pair-cell">
            <image :src="p.afterImage" mode="aspectFill" class="pair-img" />
            <text class="pair-tag after">剪后实拍</text>
            <text v-if="p.score" class="pair-score" :class="{ warn: p.score < 85 }">还原 {{ p.score }}%</text>
          </view>
          <view class="pair-arrow"><fj-icon name="arrow-right" :size="13" color="#C95676" /></view>
        </view>
        <view v-else class="post-pair single">
          <view class="pair-cell">
            <image v-if="p.afterImage || p.aiImage" :src="p.afterImage || p.aiImage" mode="aspectFill" class="pair-img" />
            <text class="pair-tag" :class="{ after: !!p.afterImage }">
              {{ p.afterImage ? '剪后实拍' : p.badge === 'ask' ? '现在的发型' : 'AI 效果图' }}
            </text>
            <text v-if="p.afterImage && p.score" class="pair-score" :class="{ warn: p.score < 85 }">还原 {{ p.score }}%</text>
          </view>
        </view>

        <view v-if="p.tags.length" class="post-tags">
          <text v-for="t in p.tags" :key="t" class="post-tag">{{ t }}</text>
        </view>

        <view class="post-actions">
          <view class="post-act" :class="{ liked: p.liked }" @click="toggleLike(p)">
            <fj-icon name="heart" :size="16" :color="p.liked ? '#C95676' : '#8B9295'" />
            <text>{{ p.likes }}</text>
          </view>
          <view class="post-act" @click="openComments(p)">
            <fj-icon name="message" :size="16" color="#8B9295" />
            <text>{{ p.commentCount }}</text>
          </view>
          <view class="post-act try" @click="trySame(p)">
            <fj-icon name="scissors" :size="16" color="#9E3654" />试同款
          </view>
          <view class="post-act" @click="reportPost(p)">
            <fj-icon name="shield" :size="16" color="#8B9295" />
            <text>举报</text>
          </view>
        </view>
      </view>

      <view class="tiny" style="text-align:center;padding:6px 20px 14px">讨论内容为原型 mock 数据，不涉及真实用户隐私</view>
    </scroll-view>

    <fj-tabbar active="community" />
    <fj-sheet />
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'
import { useUiStore } from '@/store/ui'
import { API } from '@/api/request'
import { COMM_POSTS, COMM_FILTERS, COMM_TOPICS, STYLES } from '@/mock/constants'

const app = useAppStore()
const ui = useUiStore()

const filters = COMM_FILTERS
const topics = COMM_TOPICS
const activeFilter = ref('全部')
const posts = ref([])

const FILTER_PARAM = {
  全部: '',
  真实作业: 'real',
  求建议: 'ask',
  女款: 'female',
  男款: 'male',
  圆脸友好: 'round'
}

function normalizePost(p, i) {
  const comments = Array.isArray(p.comments) ? p.comments : []
  const aiImage = p.aiImage || p.ai || ''
  const afterImage = p.afterImage || p.after || ''
  const isReal = p.badge === 'real' || p.badge === '剪后实拍' || !!afterImage
  const styleId = p.styleId || ''
  return {
    id: p.id || `post-${i}`,
    name: p.name || '匿名用户',
    ava: p.ava || '#C95676',
    meta: p.meta || '',
    badge: isReal ? 'real' : 'ask',
    styleId,
    styleName: p.styleName || '',
    gender: p.gender === 'male' || p.gender === 'female' ? p.gender : styleId.charAt(0) === 'm' ? 'male' : 'female',
    content: p.content || p.text || '',
    aiImage,
    afterImage,
    score: Number(p.score) || 0,
    tags: Array.isArray(p.tags) ? p.tags : [],
    likes: Number(p.likes) || 0,
    liked: !!p.liked,
    commentCount: comments.length || Number(p.commentCount) || 0,
    comments
  }
}

function isRealPost(p) {
  return !!p.afterImage
}

function matchFilter(p, f) {
  if (f === '全部') return true
  if (f === '真实作业') return isRealPost(p)
  if (f === '求建议') return !isRealPost(p)
  if (f === '女款') return p.gender === 'female'
  if (f === '男款') return p.gender === 'male'
  if (f === '圆脸友好') return p.tags.indexOf('圆脸友好') > -1
  return true
}

const visiblePosts = computed(() => posts.value.filter((p) => matchFilter(p, activeFilter.value)))

function filterCount(f) {
  return posts.value.filter((p) => matchFilter(p, f)).length
}

function metaLine(p) {
  return [p.meta, p.styleName].filter(Boolean).join(' · ')
}

async function loadPosts() {
  let list = []
  try {
    const res = await API.posts(FILTER_PARAM[activeFilter.value] || '')
    list = Array.isArray(res) ? res : (res && (res.posts || res.list)) || []
  } catch (e) {
    list = []
  }
  if (!Array.isArray(list) || !list.length) list = COMM_POSTS
  posts.value = list.map(normalizePost)
}

function setFilter(f) {
  activeFilter.value = f
}

function resetFilters() {
  activeFilter.value = '全部'
}

function pickTopic(t) {
  ui.toast(`${t} 话题 · 原型演示`)
}

function openCompose(mode) {
  ui.composeMode = mode === 'ask' ? 'ask' : 'real'
  ui.openSheet('compose')
}

async function toggleLike(p) {
  const beforeLiked = p.liked
  const beforeLikes = p.likes
  p.liked = !p.liked
  p.likes = Math.max(0, p.likes + (p.liked ? 1 : -1))
  try {
    await API.likePost(p.id)
    if (p.liked) ui.toast('已点赞 · 去评论区聊聊还原度')
  } catch (e) {
    p.liked = beforeLiked
    p.likes = beforeLikes
  }
}

function openComments(p) {
  // 传一份普通对象，避免响应式引用在弹层里取不到评论数据
  ui.commentPost = {
    id: p.id,
    name: p.name,
    styleName: p.styleName,
    comments: (p.comments || []).slice()
  }
  ui.openSheet('comments')
}

function reportPost(p) {
  if (!/^\d+$/.test(String(p.id))) {
    ui.toast('示例内容不支持举报')
    return
  }
  const reasons = ['内容违规', '广告引流', '侵犯隐私', '其他']
  uni.showActionSheet({
    itemList: reasons,
    success: async (res) => {
      const reason = reasons[res.tapIndex] || '用户举报'
      try {
        await API.reportPost(p.id, reason)
        ui.toast('已提交举报，我们会尽快处理')
      } catch (e) {
        /* request 已统一提示 */
      }
    }
  })
}

function trySame(p) {
  const gender = p.gender === 'male' ? 'male' : 'female'
  const list = STYLES[gender] || []
  const style = list.find((s) => s.id === p.styleId) || list.find((s) => s.name === p.styleName)
  if (!style) {
    ui.toast('这款发型暂时下架了')
    return
  }
  app.setSelection({ gender, mode: 'hair', styleIds: [style.id], colorId: '' })
  uni.navigateTo({ url: '/pages/upload/upload' })
}

function onPostCreated() {
  loadPosts()
}

function onCommentCreated(id) {
  const p = posts.value.find((x) => String(x.id) === String(id))
  if (p) p.commentCount += 1
}

onMounted(() => {
  uni.$on('fajing:post-created', onPostCreated)
  uni.$on('fajing:comment-created', onCommentCreated)
  loadPosts()
})

onUnload(() => {
  uni.$off('fajing:post-created', onPostCreated)
  uni.$off('fajing:comment-created', onCommentCreated)
})
</script>

<style lang="scss" scoped>
.ch-num-unit {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0;
  margin-left: 4px;
  opacity: 0.75;
}
.chip-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-right: 16px;
}
.topic-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding-right: 16px;
}
.pair-img {
  width: 100%;
  height: 100%;
  display: block;
}
.ai-mark {
  flex: none;
  padding: 1px 5px;
  font-size: 9px;
  font-weight: 700;
  line-height: 1.4;
  color: #7a7276;
  background: #f1edef;
  border-radius: 4px;
}
</style>
