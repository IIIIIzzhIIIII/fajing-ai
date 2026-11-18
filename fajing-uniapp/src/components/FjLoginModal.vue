<template>
  <view class="lm-mask">
    <view class="lm-card">
      <FjAuthGuide
        v-if="loaded"
        :initialNickname="nickname"
        :initialAvatar="avatar"
        :initialPhone="phone"
        @done="emit('done')"
        @cancel="emit('cancel')"
      />
      <view v-else class="lm-loading">加载中…</view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { API } from '@/api/request'
import FjAuthGuide from '@/components/FjAuthGuide.vue'

const emit = defineEmits(['done', 'cancel'])

const loaded = ref(false)
const nickname = ref('')
const avatar = ref('')
const phone = ref('')

onMounted(async () => {
  try {
    const me = await API.me()
    nickname.value = (me && me.nickname) || ''
    avatar.value = (me && me.avatar) || ''
    phone.value = (me && me.phone) || ''
  } catch (e) {
    // 取不到资料就用空值，不阻断授权流程
  }
  loaded.value = true
})
</script>

<style lang="scss" scoped>
.lm-mask {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(23, 12, 17, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 22px;
}
.lm-card {
  width: 100%;
  max-width: 340px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 18px 50px rgba(23, 12, 17, 0.22);
}
.lm-loading {
  padding: 44px 0;
  text-align: center;
  font-size: 13px;
  color: var(--muted);
}
</style>
