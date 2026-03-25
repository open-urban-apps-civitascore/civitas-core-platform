/**
 * Helper that wraps the loginAs + try/finally cleanup pattern.
 *
 * Usage:
 *   test('my test', ({ browser }) => withTestUser(browser, user, async (page) => {
 *     await page.goto('/datasets')
 *     await expect(page.getByTestId('datasetsTable')).toBeVisible()
 *   }))
 */
import type { Browser, Page } from '@playwright/test'

import type { TestUserProfile } from '../../playwright/helpers/api'
import { loginAs } from '../../playwright/helpers/auth'

export const withTestUser = async (browser: Browser, user: TestUserProfile, fn: (page: Page) => Promise<void>) => {
  const { page, context } = await loginAs(browser, {
    email: user.email,
    password: user.password,
  })

  try {
    await fn(page)
  } finally {
    await page.close()
    await context.close()
  }
}
