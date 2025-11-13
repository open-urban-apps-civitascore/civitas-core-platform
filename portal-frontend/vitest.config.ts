import react from '@vitejs/plugin-react'
import tsconfigPaths from 'vite-tsconfig-paths'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react(), tsconfigPaths()],
  test: {
    environment: 'happy-dom',
    exclude: ['node_modules', 'e2e'],
    passWithNoTests: true,
    setupFiles: './vitest.setup.ts',
    globals: true,
    reporters: ['default', 'junit'],
    outputFile: {
      junit: './test-results/junit.xml',
    },
    coverage: {
      provider: 'istanbul',
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'node_modules/',
        'e2e/',
        'src/**/*.d.ts',
        'src/**/*.config.ts',
        '**/*.test.{ts,tsx}',
        '**/*.spec.{ts,tsx}',
      ],
      reporter: ['text', 'cobertura'],
      reportsDirectory: './coverage',
    },
  },
})
