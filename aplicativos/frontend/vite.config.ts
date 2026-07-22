import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const raizDoRepositorio = fileURLToPath(new URL('../..', import.meta.url))
  const ambiente = loadEnv(mode, raizDoRepositorio, '')

  return {
    envDir: raizDoRepositorio,
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      proxy: {
        '/api': {
          target: ambiente.VITE_ALVO_API || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
