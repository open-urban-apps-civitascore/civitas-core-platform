import { expect, test } from './base-test'

test.describe('Smoke Tests', () => {
  test('authenticated user sees the dashboard', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL('/')
    await expect(page).toHaveTitle(/CIVITAS\/Core v2/)

    const header = page.locator('header')
    await expect(header).toBeVisible()

    const sidebar = page.getByLabel('Main navigation')
    await expect(sidebar).toBeVisible()

    const main = page.locator('main')
    await expect(main).toBeVisible()
  })

  test('BFF proxy returns data from backend API', async ({ page }) => {
    // Navigate to the users page so backend data is rendered visibly
    await page.goto('/users')
    await expect(page.getByTestId('usersPage')).toBeVisible()
    await expect(page.getByTestId('usersTable')).toBeVisible()
  })
})
