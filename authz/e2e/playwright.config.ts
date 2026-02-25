/**
 * Playwright config for CIVITAS AuthZ E2E tests
 *
 * Standalone test suite for authorization integration testing.
 * Tests the full flow: Browser -> Next.js BFF -> APISIX -> OPA -> AuthZ Repository
 *
 * Prerequisites:
 *   - Frontend running on http://localhost:3000
 *   - Full authz stack (APISIX, OPA, AuthZ Repository)
 *   - Test users seeded in Keycloak
 *
 * Environment variables:
 *   E2E_AUTHZ_ADMIN_EMAIL, E2E_AUTHZ_ADMIN_PASSWORD
 *   E2E_AUTHZ_READER_EMAIL, E2E_AUTHZ_READER_PASSWORD
 *   E2E_AUTHZ_NOPERMS_EMAIL, E2E_AUTHZ_NOPERMS_PASSWORD
 */
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Single worker for authz tests - they share auth state */
  workers: 1,

  /* Reporter */
  reporter: [['html', { outputFolder: './playwright-report' }], ['list']],

  /* Shared settings for all projects */
  use: {
    baseURL: process.env.FRONTEND_URL || 'http://localhost:3000',

    /* Collect trace on failure */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'on',
  },

  projects: [
    {
      name: 'authz-integration',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
