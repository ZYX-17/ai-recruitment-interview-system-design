import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ resolvers: [ElementPlusResolver()] }),
    Components({ resolvers: [ElementPlusResolver()] }),
  ],
  server: {
    port: 5173,
    proxy: {
      // 业务 API → 网关
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
      // 房间 WS（聊天/字幕/系统事件）→ room-service
      '/ws/room': { target: 'ws://127.0.0.1:8085', ws: true, changeOrigin: true },
      // ASR WSS → ai-worker（scope=asr 短时令牌；不经过网关）
      '/api/ai/v1/asr': { target: 'ws://127.0.0.1:8001', ws: true, changeOrigin: true },
      // LiveKit 信令（独立子域名 livekit.<domain> 或同域 /livekit rewrite 到 /rtc）
      '/livekit': { target: 'ws://127.0.0.1:7880', ws: true, changeOrigin: true, rewrite: (p) => p.replace(/^\/livekit/, '/rtc') },
    },
  },
})
