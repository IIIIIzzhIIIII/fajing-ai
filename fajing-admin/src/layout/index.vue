<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Coin, DataLine, Document, MagicStick, OfficeBuilding, Picture, Scissor, SetUp, Setting,
  Shop, SwitchButton, Ticket, Tickets, TrendCharts, User, UserFilled, Wallet
} from '@element-plus/icons-vue'
import { clearAdminInfo, clearToken, getAdminInfo } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const adminInfo = getAdminInfo() || {}
const isStore = adminInfo.role === 'STORE_MANAGER'

const platformSections = [
  {
    title: '平台工作空间',
    items: [
      { path: '/dashboard', title: '数据看板', icon: DataLine },
      { path: '/channels', title: '渠道与传播', icon: TrendCharts },
      { path: '/orders', title: '订单管理', icon: Tickets },
      { path: '/coupons', title: '团购券核销', icon: Ticket },
      { path: '/users', title: '用户管理', icon: User }
    ]
  },
  {
    title: '平台管理',
    items: [
      { path: '/platform/tenants', title: '租户与门店', icon: OfficeBuilding },
      { path: '/platform/commissions', title: '佣金台账', icon: Coin }
    ]
  },
  {
    title: '前台配置',
    items: [
      { path: '/hairstyles', title: '发型库', icon: Scissor },
      { path: '/ai-model', title: 'AI 模特照', icon: Picture }
    ]
  },
  {
    title: '系统管理',
    items: [
      { path: '/settings', title: '基础设置', icon: Setting },
      { path: '/ai', title: 'AI 参数配置', icon: MagicStick },
      { path: '/pay', title: '支付参数配置', icon: Wallet },
      { path: '/rules', title: '交易与售后规则', icon: SetUp },
      { path: '/logs', title: '发布与日志', icon: Document }
    ]
  }
]

const storeSections = [
  {
    title: '门店工作空间',
    items: [
      { path: '/store/dashboard', title: '门店工作台', icon: Shop },
      { path: '/store/verify', title: '核销台', icon: Ticket },
      { path: '/store/orders', title: '门店订单', icon: Tickets }
    ]
  },
  {
    title: '经营管理',
    items: [
      { path: '/store/coupons', title: '门店券管理', icon: Tickets },
      { path: '/store/staff', title: '员工管理', icon: UserFilled }
    ]
  },
  {
    title: '门店配置',
    items: [
      { path: '/store/settings', title: '门店设置', icon: Setting }
    ]
  }
]

const menuSections = computed(() => (isStore ? storeSections : platformSections))
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '发镜 AI 管理后台')
const roleLabel = computed(() => (isStore ? '店长后台' : '平台总后台'))
const userName = computed(() => adminInfo.displayName || adminInfo.username || '管理员')
const storeLabel = computed(() => (isStore ? adminInfo.storeName || '' : ''))
const avatarText = computed(() => (isStore ? '店' : '管'))

function handleLogout() {
  clearToken()
  clearAdminInfo()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-logo">发</div>
        <div class="brand-text">
          <div class="brand-title">发镜 AI</div>
          <div class="brand-sub">{{ roleLabel }}</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        class="side-menu"
        background-color="transparent"
        text-color="rgba(255, 255, 255, 0.66)"
        active-text-color="#ffffff"
      >
        <template v-for="section in menuSections" :key="section.title">
          <div class="menu-section">{{ section.title }}</div>
          <el-menu-item v-for="item in section.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="side-footer">发镜 AI · {{ roleLabel }}</div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="page-title">{{ pageTitle }}</div>
        <div class="topbar-right">
          <div class="user-entry">
            <span class="avatar">{{ avatarText }}</span>
            <span class="user-name">{{ userName }}</span>
            <span v-if="storeLabel" class="user-store">{{ storeLabel }}</span>
          </div>
          <el-button text :icon="SwitchButton" class="logout-btn" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </header>

      <main class="content">
        <div class="page-card">
          <router-view />
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
$sidebar-width: 232px;

.app-layout {
  display: flex;
  min-height: 100vh;
  background-color: #f5f3f5;
}

.sidebar {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  width: $sidebar-width;
  background: linear-gradient(180deg, #241c20 0%, #1b1518 100%);
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  height: 76px;
  padding: 0 22px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.brand-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 20px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #d46a88 0%, #a83f5e 100%);
  border-radius: 10px;
  box-shadow: 0 6px 14px rgba(201, 86, 118, 0.35);
}

.brand-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 0.5px;
}

.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 1px;
}

.side-menu {
  flex: 1;
  padding: 8px 12px 16px;
  overflow-y: auto;

  &::-webkit-scrollbar {
    width: 0;
  }

  .menu-section {
    padding: 14px 10px 6px;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.32);
    letter-spacing: 1.5px;

    &:first-child {
      padding-top: 4px;
    }
  }

  :deep(.el-menu-item) {
    height: 44px;
    margin-bottom: 6px;
    border-radius: 8px;

    &:hover {
      background-color: rgba(255, 255, 255, 0.06);
      color: #fff;
    }

    &.is-active {
      background: linear-gradient(90deg, rgba(201, 86, 118, 0.95) 0%, rgba(169, 63, 94, 0.9) 100%);
      box-shadow: 0 6px 16px rgba(201, 86, 118, 0.28);
    }
  }
}

.side-footer {
  padding: 18px 22px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.3);
}

.main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  margin-left: $sidebar-width;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 28px;
  background-color: #fff;
  border-bottom: 1px solid #f0e9ec;
}

.page-title {
  font-size: 17px;
  font-weight: 600;
  color: #2b2628;
}

.topbar-right {
  display: flex;
  gap: 16px;
  align-items: center;
}

.user-entry {
  display: flex;
  gap: 8px;
  align-items: center;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  font-size: 13px;
  color: #fff;
  background: linear-gradient(135deg, #d46a88, #a83f5e);
  border-radius: 50%;
}

.user-name {
  font-size: 14px;
  color: #3b3337;
}

.user-store {
  padding: 2px 8px;
  font-size: 11px;
  color: #a83f5e;
  background: #fdf4f7;
  border-radius: 999px;
}

.logout-btn {
  color: #7d7278;

  &:hover {
    color: #c95676;
    background-color: #fdf4f7;
  }
}

.content {
  flex: 1;
  padding: 22px 28px 32px;
}

.page-card {
  min-height: calc(100vh - 118px);
  padding: 24px;
  background-color: #fff;
  border-radius: 14px;
  box-shadow: 0 6px 24px rgba(43, 38, 40, 0.05);
}
</style>
