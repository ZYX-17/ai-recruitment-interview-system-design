<template>
  <div class="room-page">
    <h3>在线面试房间（骨架，Owner：C）</h3>
    <p>roomId: {{ roomId }}</p>
    <p>
      W7~8 实现：rtc/livekit.ts 封装（Simulcast 低轨 / 1v5 网格布局 / 屏幕共享 / 断线重连 / 进房预检）；
      AudioWorklet 16kHz PCM → WSS /api/ai/v1/asr/{sessionId}（scope=asr 短时令牌）；聊天/字幕/系统事件 WS。
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const roomId = computed(() => route.params.roomId)
</script>
