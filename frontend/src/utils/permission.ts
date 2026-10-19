/**
 * 权限判断纯函数：可在路由守卫、组件、工具函数中复用。
 *
 * 注意：前端权限只决定“是否渲染 / 是否跳转”，不是安全边界；
 * 每个受保护的后端接口仍必须按权限码再校验（auth-service + 各服务）。
 */
export type PermissionCode = string | readonly string[]
export type PermissionMode = 'any' | 'all'

/**
 * @param owned    当前用户拥有的权限码
 * @param required 需要的权限码；数组时按 mode 判断
 * @param mode     any=任一满足（默认），all=全部满足
 */
export function checkPermission(
  owned: readonly string[],
  required: PermissionCode,
  mode: PermissionMode = 'any',
): boolean {
  const codes = typeof required === 'string' ? [required] : required
  if (codes.length === 0) return true
  // 可选约定：后端下发 "*" 表示超级管理员拥有全部权限
  if (owned.includes('*')) return true
  return mode === 'all'
    ? codes.every((code) => owned.includes(code))
    : codes.some((code) => owned.includes(code))
}
