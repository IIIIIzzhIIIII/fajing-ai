<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { useAppStore } from './store/app'
import { login } from './api/request'

onLaunch(() => {
  // 进入小程序/刷新 H5 时，恢复一次本地额度状态
  useAppStore().restore()
  // 静默登录：失败不阻断进入首页，首页操作时会再重试
  login().catch(() => {})
})
</script>

<style lang="scss">
@import './styles/base.scss';
</style>
