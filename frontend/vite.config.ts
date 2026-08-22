import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The API is proxied rather than called cross-origin, so the browser only ever
// talks to this dev server. That keeps CORS configuration out of the service for
// local development; a real deployment would serve both from one origin or add an
// explicit CORS policy.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
