<template>
  <text v-if="show" class="write-on" :class="`wm-${position}`">{{ text }}</text>
</template>

<script setup>
defineProps({
  show: { type: Boolean, default: true },
  text: { type: String, default: '发镜 AI · AI 生成' },
  position: { type: String, default: 'br' }
})
</script>

<style lang="scss" scoped>
.write-on.wm-br {
  right: 10px;
  bottom: 10px;
}
.write-on.wm-bl {
  right: auto;
  left: 10px;
  bottom: 10px;
}
.write-on.wm-tl {
  right: auto;
  left: 10px;
  top: 10px;
  bottom: auto;
}
.write-on.wm-tr {
  top: 10px;
  bottom: auto;
}
</style>
