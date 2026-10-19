import type { Directive } from 'vue'
import { useUserStore } from '../stores/user'

/**
 * 按钮级权限指令：v-permission="'interview:application:transfer'"
 * 无权限则移除元素。注意：前端隐藏按钮不是安全边界，后端必须再校验（auth-service 权限码）。
 */
export const permissionDirective: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const store = useUserStore()
    const required = Array.isArray(binding.value) ? binding.value : [binding.value]
    const allowed = required.some((code) => store.hasPermission(code))
    if (!allowed) {
      el.parentNode?.removeChild(el)
    }
  },
}
