<script setup lang="ts">
import { computed } from 'vue'
import { usePermission } from '../composables/usePermission'
import type { PermissionCode, PermissionMode } from '../utils/permission'

/**
 * 权限包装组件：内部用 v-if 决定是否渲染插槽，适合包住一段较长的模板，
 * 或需要统一处理“无权限时显示占位”的场景。
 *
 *   <PermissionGate code="interview:application:transfer">
 *     <el-button>转岗</el-button>
 *   </PermissionGate>
 *
 *   <PermissionGate :code="['a:b', 'c:d']" mode="all" #fallback>
 *     <span>无权限</span>
 *   </PermissionGate>
 */
const props = withDefaults(
  defineProps<{
    code: PermissionCode
    mode?: PermissionMode
  }>(),
  { mode: 'any' },
)

const { hasPermission } = usePermission()
const allowed = computed(() => hasPermission(props.code, props.mode))
</script>

<template>
  <slot v-if="allowed" />
  <slot v-else name="fallback" />
</template>
