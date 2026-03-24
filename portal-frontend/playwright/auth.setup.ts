import { test as setup } from '@playwright/test'

import { TEST_PASSWORD, TEST_USERNAME } from '../playwright.config'

const authFile = './playwright/.auth/user.json'

setup('authenticate', async ({ page }) => {
  setup.setTimeout(60_000)
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()

  // Wait for Keycloak login form to fully load before interacting
  const usernameField = page.getByRole('textbox', { name: 'Username or email' })
  await usernameField.waitFor({ state: 'visible', timeout: 30_000 })

  await usernameField.fill(TEST_USERNAME)
  await page.getByRole('textbox', { name: 'Password' }).fill(TEST_PASSWORD)

  await page.getByRole('button', { name: 'Sign In' }).click()
  await page.waitForURL('/', { timeout: 30_000 })

  await page.context().storageState({ path: authFile })
})
