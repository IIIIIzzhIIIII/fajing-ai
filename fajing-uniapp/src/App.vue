<script setup>
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 进入小程序/刷新 H5 时，恢复一次本地额度状态
  const store = useAppStore()
  store.restore()
})
</script>

<script>
import { useAppStore } from './store/app'
export default {}
</script>

<style lang="scss">
@import './styles/base.scss';
</style>
