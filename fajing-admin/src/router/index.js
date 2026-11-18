import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/utils/auth'
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
  const logged = Boolean(getToken())
  if (to.path === '/login') {
    return logged ? { path: '/' } : true
  }
  if (!logged) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title
    ? `${to.meta.title} · 发镜 AI 管理后台`
    : '发镜 AI 管理后台'
})

export default router
