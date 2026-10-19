import { useUserStore } from '../stores/user'
import {
  checkPermission,
  type PermissionCode,
  type PermissionMode,
} from '../utils/permission'

/**
 * 按钮/区块级权限判断。在模板里用 v-if，元素从一开始就不会被创建：
 *
 *   <el-button v-if="hasPermission('interview:application:transfer')">转岗</el-button>
 *   <el-button v-if="hasAny(['room:room:create', 'room:room:update'])">编辑房间</el-button>
 *   <el-button v-if="hasAll(['interview:offer:create', 'interview:offer:approve'])">提交审批</el-button>
 *
 * 权限变化（登录、切换账号、后端刷新权限）时，模板读取的是 Pinia 的响应式状态，
 * 会随 store 变化自动重新渲染；旧的自定义指令只在 mounted 执行一次，做不到这一点。
 */
export function usePermission() {
  const userStore = useUserStore()

  function hasPermission(code: PermissionCode, mode: PermissionMode = 'any'): boolean {
    return checkPermission(userStore.permissions, code, mode)
  }

  function hasAny(codes: readonly string[]): boolean {
    return checkPermission(userStore.permissions, codes, 'any')
  }

  function hasAll(codes: readonly string[]): boolean {
    return checkPermission(userStore.permissions, codes, 'all')
  }

  return { hasPermission, hasAny, hasAll }
}
