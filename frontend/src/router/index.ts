import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

// 静态路由（登录/布局/房间）；业务 36 页由 IAM 动态菜单驱动注册（W2：登录后拉取 iam_permission 菜单树）
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/home/index.vue'),
    // TODO(W2)：children 由后端菜单树动态生成（router.addRoute）
  },
  {
    path: '/room/:roomId',
    name: 'Room',
    component: () => import('../views/room/index.vue'),
    meta: { title: '在线面试房间' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫：未登录跳 /login；TODO(W2) 接入 token 校验与动态菜单注册
router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('access_token')
  if (!to.meta.public && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  next()
})

export default router
