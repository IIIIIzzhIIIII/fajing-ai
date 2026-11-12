<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { loginAdmin } from '@/api'
import { setToken } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: ''
})

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
    setToken(data.token)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
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
          <li>社区帖子内容统一管理</li>
          <li>订单数据一目了然</li>
        </ul>
      </section>

      <section class="form-panel">
        <div class="form-head">
          <h2>登录管理后台</h2>
          <p>请使用管理员账号继续</p>
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

        <div class="account-hint">初始账号由服务端 ADMIN_INIT_PASSWORD 配置，未配置时不会创建管理员</div>
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
