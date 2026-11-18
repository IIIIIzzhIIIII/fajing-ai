<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { useAppStore } from './store/app'
import { login, API } from './api/request'
import { initPrivacyGuard } from './utils/privacy'
import { loadSiteSettings } from './utils/site'

onLaunch(async () => {
  const app = useAppStore()
  // 隐私授权守卫要在静默登录之前注册，否则首次进入拿不到授权回调
  initPrivacyGuard()
  // 前台文案与价格属于公开配置，先拉一次；失败沿用缓存与默认值
  loadSiteSettings()
  // 恢复本地缓存后立即以服务端额度覆盖，避免本地缓存与实际额度不一致
  app.restore()
  try {
    // 静默登录：失败不阻断进入首页，首页操作时会再重试
    await login()
    app.setQuota(await API.quota())
  } catch (e) {
    // 登录或额度同步失败不阻断使用，后续操作会再次触发
  }
})
</script>

<style lang="scss">
@import './styles/base.scss';
</style>
