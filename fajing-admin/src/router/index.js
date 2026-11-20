import { createRouter, createWebHashHistory } from 'vue-router'
import { getRole, getToken, homePath } from '@/utils/auth'
import Layout from '@/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板' }
      },
      {
        path: 'channels',
        name: 'Channels',
        component: () => import('@/views/channels/index.vue'),
        meta: { title: '渠道与传播' }
      },
      {
        path: 'hairstyles',
        name: 'Hairstyles',
        component: () => import('@/views/hairstyles/index.vue'),
        meta: { title: '发型库' }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/logs/index.vue'),
        meta: { title: '发布与日志' }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/index.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'coupons',
        name: 'Coupons',
        component: () => import('@/views/coupons/index.vue'),
        meta: { title: '团购券核销' }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('@/views/users/index.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'ai-model',
        name: 'AiModel',
        component: () => import('@/views/ai-model/index.vue'),
        meta: { title: 'AI 模特照' }
      },
      {
        path: 'pay',
        name: 'PayConfig',
        component: () => import('@/views/pay/index.vue'),
        meta: { title: '支付参数配置' }
      },
      {
        path: 'rules',
        name: 'TradeRule',
        component: () => import('@/views/rules/index.vue'),
        meta: { title: '交易与售后规则' }
      },
      {
        path: 'settings',
        name: 'SiteSettings',
        component: () => import('@/views/settings/index.vue'),
        meta: { title: '基础设置' }
      },
      {
        path: 'ai',
        name: 'AiConfig',
        component: () => import('@/views/ai/index.vue'),
        meta: { title: 'AI 参数配置' }
      },
      {
        path: 'platform/tenants',
        name: 'PlatformTenants',
        component: () => import('@/views/platform/tenants.vue'),
        meta: { title: '租户与门店' }
      },
      {
        path: 'platform/commissions',
        name: 'PlatformCommissions',
        component: () => import('@/views/platform/commissions.vue'),
        meta: { title: '佣金台账' }
      },
      {
        path: 'store/dashboard',
        name: 'StoreDashboard',
        component: () => import('@/views/store/dashboard.vue'),
        meta: { title: '门店工作台' }
      },
      {
        path: 'store/coupons',
        name: 'StoreCoupons',
        component: () => import('@/views/store/coupons.vue'),
        meta: { title: '门店券管理' }
      },
      {
        path: 'store/staff',
        name: 'StoreStaff',
        component: () => import('@/views/store/staff.vue'),
        meta: { title: '员工管理' }
      },
      {
        path: 'store/verify',
        name: 'StoreVerify',
        component: () => import('@/views/store/verify.vue'),
        meta: { title: '核销台' }
      },
      {
        path: 'store/orders',
        name: 'StoreOrders',
        component: () => import('@/views/store/orders.vue'),
        meta: { title: '门店订单' }
      },
      {
        path: 'store/settings',
        name: 'StoreSettings',
        component: () => import('@/views/store/settings.vue'),
        meta: { title: '门店设置' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  // 登录态按路由归属区分，总后台与店长后台可在不同标签页同时登录
  const scope = to.path.startsWith('/store') ? 'STORE_MANAGER' : 'PLATFORM_ADMIN'
  const logged = Boolean(getToken(scope))
  const role = logged ? getRole(scope) : 'PLATFORM_ADMIN'
  if (to.path === '/login') {
    // 登录页按 redirect 或 role 参数判断目标后台，支持两个后台各自独立登录
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : ''
    const storeLogin = to.query.role === 'store' || redirect.startsWith('/store')
    const targetScope = storeLogin ? 'STORE_MANAGER' : scope
    if (getToken(targetScope)) {
      return { path: homePath(getRole(targetScope)) }
    }
    return true
  }
  if (!logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 店长只能访问 /store/**，平台总管理员不能进入店长专属页面
  if (role === 'STORE_MANAGER' && !to.path.startsWith('/store')) {
    return { path: '/store/dashboard' }
  }
  if (role !== 'STORE_MANAGER' && to.path.startsWith('/store')) {
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} · 发镜 AI 管理后台`
    : '发镜 AI 管理后台'
})

export default router
