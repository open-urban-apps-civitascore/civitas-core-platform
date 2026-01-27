import { test as setup } from '../e2e/base-test'
import { TEST_PASSWORD, TEST_USERNAME } from '../playwright.config'

const authFile = './playwright/.auth/user.json'

setup('authenticate', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()

  await page.getByRole('textbox', { name: 'Username or email' }).click()
  await page.getByRole('textbox', { name: 'Username or email' }).fill(TEST_USERNAME)

  await page.getByRole('textbox', { name: 'Password' }).click()
  await page.getByRole('textbox', { name: 'Password' }).fill(TEST_PASSWORD)

  await page.getByRole('button', { name: 'Sign In' }).click()
  await page.waitForURL('/')

  await page.context().storageState({ path: authFile })
})
