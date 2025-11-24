import { defineConfig, devices } from '@playwright/test'
import dotenv from 'dotenv'
import path from 'path'
import { fileURLToPath } from 'url'
/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
const filename = fileURLToPath(import.meta.url)
const dirname = path.dirname(filename)
dotenv.config({ path: path.resolve(dirname, '.env.local'), quiet: true })

if (
  !process.env.E2E_USERNAME ||
  !process.env.E2E_PASSWORD ||
  !process.env.E2E_EMAIL ||
  !process.env.E2E_FIRSTNAME ||
  !process.env.E2E_LASTNAME ||
  !process.env.E2E_MOCK_FIRSTNAME ||
  !process.env.E2E_MOCK_LASTNAME ||
  !process.env.E2E_MOCK_EMAIL ||
  !process.env.JSON_SERVER_HOST ||
  !process.env.JSON_SERVER_PORT
) {
  throw new Error(
    'E2E_USERNAME, E2E_PASSWORD, E2E_EMAIL, E2E_FIRSTNAME, E2E_LASTNAME, E2E_MOCK_FIRSTNAME, E2E_MOCK_LASTNAME, E2E_MOCK_EMAIL, JSON_SERVER_HOST and JSON_SERVER_PORT environment variables are required',
  )
}

export const TEST_USERNAME = process.env.E2E_USERNAME
export const TEST_PASSWORD = process.env.E2E_PASSWORD
export const TEST_EMAIL = process.env.E2E_EMAIL
export const TEST_FIRSTNAME = process.env.E2E_FIRSTNAME
export const TEST_LASTNAME = process.env.E2E_LASTNAME
export const E2E_MOCK_FIRSTNAME = process.env.E2E_MOCK_FIRSTNAME
export const E2E_MOCK_LASTNAME = process.env.E2E_MOCK_LASTNAME
export const E2E_MOCK_EMAIL = process.env.E2E_MOCK_EMAIL
export const JSON_SERVER_HOST = process.env.JSON_SERVER_HOST
export const JSON_SERVER_PORT = process.env.JSON_SERVER_PORT

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './e2e',
  /* Run tests in files in parallel */
  fullyParallel: true,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: 'http://localhost:3000',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },
  globalSetup: './playwright/globalTeardown.ts',
  globalTeardown: './playwright/globalTeardown.ts',

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'authSetup',
      testDir: './playwright',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: './playwright/.auth/user.json',
      },
      dependencies: ['authSetup'],
    },

    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        storageState: './playwright/.auth/user.json',
      },
      dependencies: ['authSetup'],
    },

    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        storageState: './playwright/.auth/user.json',
      },
      dependencies: ['authSetup'],
    },

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: { ...devices['Desktop Edge'], channel: 'msedge' },
    // },
    // {
    //   name: 'Google Chrome',
    //   use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    // },
  ],

  /* Run your local dev server before starting the tests */
  webServer: {
    command: 'pnpm dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
  },
})
