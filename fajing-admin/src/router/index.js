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
        path: 'hairstyles',
        name: 'Hairstyles',
        component: () => import('@/views/hairstyles/index.vue'),
        meta: { title: '发型库' }
      },
      {
        path: 'logs',
        name: 'Logs',
        component: () => import('@/views/logs/index.vue'),
        meta: { title: '操作日志' }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/index.vue'),
        meta: { title: '订单列表' }
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
        meta: { title: '支付配置' }
      },
      {
        path: 'ai',
        name: 'AiConfig',
        component: () => import('@/views/ai/index.vue'),
        meta: { title: 'AI 设置' }
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
