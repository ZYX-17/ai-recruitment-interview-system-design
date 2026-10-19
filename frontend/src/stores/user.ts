import { defineStore } from 'pinia'

interface UserState {
  userId: string
  displayName: string
  roles: string[]        // SUPER_ADMIN/HR_ADMIN/RECRUITER/INTERVIEWER/CANDIDATE
  permissions: string[]  // 权限码（菜单/按钮/API 三态）
  menus: unknown[]       // 动态菜单树（iam_permission，MENU 类型）
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    userId: '',
    displayName: '',
    roles: [],
    permissions: [],
    menus: [],
  }),
  actions: {
    // TODO(W2)：POST /api/auth/login → 存双令牌 → GET /api/auth/menus → 动态注册路由
    hasPermission(code: string): boolean {
      return this.permissions.includes(code)
    },
    logout() {
      localStorage.removeItem('access_token')
      localStorage.removeItem('refresh_token')
      this.$reset()
    },
  },
})
