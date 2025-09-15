import react from '@vitejs/plugin-react'
import path from 'path'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'happy-dom',
    exclude: ['node_modules'],
    passWithNoTests: true,
    setupFiles: './vitest.setup.ts',
    globals: true,
  },
  resolve: {
    alias: [
      {
        find: '@',
        replacement: path.resolve(__dirname, 'src'),
      },
      {
        find: '@/auth',
        replacement: path.resolve(__dirname, 'auth.ts'),
      },
      {
        find: '@/auth-config',
        replacement: path.resolve(__dirname, 'auth.config.ts'),
      },
    ],
  },
})
