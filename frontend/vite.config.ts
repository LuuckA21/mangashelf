import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // In development Vite serves on 5173 while Spring runs on 8080. Proxying
    // /api keeps both on one origin, so the session cookie behaves exactly as
    // it will in production behind nginx.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
})
