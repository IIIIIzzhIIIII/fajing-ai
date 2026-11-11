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
    redirect: '/hairstyles',
    children: [
      {
        path: 'hairstyles',
        name: 'Hairstyles',
        component: () => import('@/views/hairstyles/index.vue'),
        meta: { title: '发型库' }
      },
      {
        path: 'posts',
        name: 'Posts',
        component: () => import('@/views/posts/index.vue'),
        meta: { title: '帖子管理' }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/index.vue'),
        meta: { title: '订单列表' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/hairstyles'
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
