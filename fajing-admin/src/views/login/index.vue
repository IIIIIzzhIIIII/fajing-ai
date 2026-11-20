<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { loginAdmin } from '@/api'
import { homePath, setAdminInfo, setToken } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: ''
})

const isStoreLogin = computed(() => {
  if (route.query.role === 'store') return true
  return typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/store')
})

watch(
  isStoreLogin,
  (store) => {
    form.username = store ? 'store1' : 'admin'
  },
  { immediate: true }
)

function switchRole(store) {
  router.replace({
    path: '/login',
    query: store ? { role: 'store' } : {}
  })
}

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await loginAdmin({ username: form.username.trim(), password: form.password })
    setToken(data.token, data.role)
    setAdminInfo(data)
    ElMessage.success(data.role === 'STORE_MANAGER' ? '店长登录成功' : '平台管理员登录成功')
    const fallback = homePath(data.role)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : fallback
    const redirectIsStore = redirect.startsWith('/store')
    const target = data.role === 'STORE_MANAGER'
      ? (redirectIsStore ? redirect : fallback)
      : (redirectIsStore ? fallback : redirect)
    router.replace(target)
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="glow glow-left"></div>
    <div class="glow glow-right"></div>

    <div class="login-card">
      <section class="brand-panel">
        <div class="brand-mark">发</div>
        <h1 class="brand-name">发镜 AI</h1>
        <p class="brand-slogan">AI 换发型 · 让每一次改变都有据可依</p>
        <ul class="brand-points">
          <li>发型库上下架即时生效</li>
          <li>发型素材与 AI 配置统一管理</li>
          <li>订单数据一目了然</li>
        </ul>
      </section>

      <section class="form-panel">
        <div class="role-switch">
          <div :class="['role-tab', { on: !isStoreLogin }]" @click="switchRole(false)">平台总后台</div>
          <div :class="['role-tab', { on: isStoreLogin }]" @click="switchRole(true)">店长后台</div>
        </div>

        <div class="form-head">
          <h2>{{ isStoreLogin ? '登录店长后台' : '登录平台总后台' }}</h2>
          <p>{{ isStoreLogin ? '管理门店券、员工与核销，查看门店经营数据' : '管理租户、门店、佣金与平台运营数据' }}</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
          <el-form-item label="账号" prop="username">
            <el-input
              v-model="form.username"
              :prefix-icon="User"
              placeholder="请输入账号"
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              placeholder="请输入密码"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>

        <div class="account-hint">
          {{ isStoreLogin ? '店长演示账号 store1 / store123' : '平台总管理员演示账号 admin / admin123' }}
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  overflow: hidden;
  background: radial-gradient(circle at 20% 15%, #332329 0%, #1d1619 55%, #151012 100%);
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}

.glow-left {
  top: -120px;
  left: -100px;
  width: 420px;
  height: 420px;
  background: rgba(201, 86, 118, 0.55);
}

.glow-right {
  right: -140px;
  bottom: -160px;
  width: 460px;
  height: 460px;
  background: rgba(160, 92, 156, 0.35);
}

.login-card {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  width: 100%;
  max-width: 880px;
  overflow: hidden;
  background-color: #fff;
  border-radius: 20px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.35);
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 56px 46px;
  color: #fff;
  background: linear-gradient(150deg, #cf607f 0%, #a83f5e 55%, #7f2f49 100%);

  &::after {
    position: absolute;
    right: -70px;
    bottom: -90px;
    width: 260px;
    height: 260px;
    content: '';
    background: rgba(255, 255, 255, 0.08);
    border-radius: 50%;
  }
}

.brand-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 26px;
  font-size: 28px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 14px;
}

.brand-name {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
}

.brand-slogan {
  margin: 12px 0 32px;
  font-size: 13px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.82);
}

.brand-points {
  margin: 0;
  padding: 0;
  font-size: 13px;
  line-height: 2.2;
  color: rgba(255, 255, 255, 0.75);
  list-style: none;

  li::before {
    display: inline-block;
    width: 6px;
    height: 6px;
    margin-right: 10px;
    vertical-align: middle;
    content: '';
    background-color: rgba(255, 255, 255, 0.7);
    border-radius: 50%;
  }
}

.form-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 54px 52px;
}

.role-switch {
  display: flex;
  gap: 8px;
  padding: 4px;
  margin-bottom: 22px;
  background: #faf5f7;
  border-radius: 10px;
}

.role-tab {
  flex: 1;
  padding: 9px 0;
  font-size: 13px;
  color: #8d8187;
  text-align: center;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;

  &.on {
    font-weight: 600;
    color: #a83f5e;
    background: #fff;
    box-shadow: 0 2px 8px rgba(201, 86, 118, 0.14);
  }
}

.form-head {
  margin-bottom: 26px;

  h2 {
    margin: 0;
    font-size: 23px;
    font-weight: 600;
    color: #2b2628;
  }

  p {
    margin: 8px 0 0;
    font-size: 13px;
    color: #9a8f95;
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  margin-top: 6px;
  font-size: 15px;
  letter-spacing: 2px;
  border-radius: 8px;
}

.account-hint {
  margin-top: 22px;
  padding: 10px 12px;
  font-size: 12px;
  color: #9a7f88;
  text-align: center;
  background-color: #fdf5f7;
  border-radius: 8px;
}

@media (max-width: 720px) {
  .login-card {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .form-panel {
    padding: 40px 28px;
  }
}
</style>
