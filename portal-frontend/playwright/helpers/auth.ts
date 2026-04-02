/**
 * Multi-user authentication helpers for Playwright E2E tests.
 *
 * The default auth.setup.ts logs in as the admin user and stores state
 * in ./playwright/.auth/user.json. For permission testing, we need
 * to log in as different users with different permission profiles.
 *
 * Usage in tests:
 *
 *   import { loginAs } from '../../playwright/helpers/auth'
 *
 *   test('user without create permission cannot see create button', async ({ browser }) => {
 *     const page = await loginAs(browser, {
 *       email: testUser.email,
 *       password: testUser.password,
 *     })
 *     await page.goto('/users')
 *     await expect(page.getByTestId('addUserButton')).not.toBeVisible()
 *     await page.close()
 *   })
 */
import { Browser, BrowserContext, Page } from '@playwright/test'

import { TEST_PASSWORD, TEST_USERNAME } from '../../playwright.config'

/**
 * Log in as a specific user and return an authenticated page.
 *
 * Creates a fresh browser context (no stored auth state), navigates
 * through the Keycloak login flow, and returns the authenticated page.
 */
export const loginAs = async (
  browser: Browser,
  credentials: { email: string; password: string },
): Promise<{ page: Page; context: BrowserContext }> => {
  // Create a fresh context with NO stored auth state.
  // Must explicitly set storageState to undefined to override the project-level
  // storageState config (chromium project uses ./playwright/.auth/user.json).
  const context = await browser.newContext({ storageState: undefined })
  const page = await context.newPage()

  await page.goto('/login')
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()

  await page.getByRole('textbox', { name: 'Username or email' }).fill(credentials.email)
  await page.getByRole('textbox', { name: 'Password' }).fill(credentials.password)
  await page.getByRole('button', { name: 'Sign In' }).click()

  // After Keycloak login, the app redirects to the home page.
  // Wait until we're no longer on the Keycloak domain.
  await page.waitForURL(url => !url.toString().includes('localhost:8080'), { timeout: 15_000 })

  return { page, context }
}

/**
 * Log in as the default admin user (from .env.local).
 * Convenience wrapper when you need a fresh admin session without using stored state.
 */
export const loginAsAdmin = async (browser: Browser): Promise<{ page: Page; context: BrowserContext }> =>
  loginAs(browser, {
    email: TEST_USERNAME,
    password: TEST_PASSWORD,
  })
