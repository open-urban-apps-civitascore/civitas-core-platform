import { expect, test } from '@playwright/test'

import { TEST_EMAIL, TEST_FIRSTNAME, TEST_LASTNAME } from '../playwright.config'

test.describe('Page', async () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('has title', async ({ page }) => {
    // Expect a title "to contain" a substring.
    await expect(page).toHaveTitle(/CIVITAS\/Core v2/)
  })

  test('renders the header with a logo and search box', async ({ page }) => {
    const header = page.locator('header')
    await expect(header).toBeVisible()
    await expect(header.getByRole('textbox', { name: 'Search' })).toBeVisible()

    await expect(header.getByRole('link')).toBeVisible()
    await expect(header.getByRole('link')).toHaveAttribute('href', '#')
  })

  test('renders a sidebar with collapsible items', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Menu item 1' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Sub item 1' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Sub item 2' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Sub item 3' })).toBeVisible()
    await page.getByRole('listitem').filter({ hasText: 'Menu item 1ToggleSub item' }).getByRole('button').click()

    await expect(page.getByRole('link', { name: 'Sub item 1' })).not.toBeVisible()
    await expect(page.getByRole('link', { name: 'Sub item 2' })).not.toBeVisible()
    await expect(page.getByRole('link', { name: 'Sub item 3' })).not.toBeVisible()
  })

  test('renders a footer with account information and popup', async ({ page }) => {
    const initials = TEST_FIRSTNAME.charAt(0).toUpperCase()

    const accountButton = page.getByRole('button', {
      name: `${initials} ${TEST_FIRSTNAME} ${TEST_LASTNAME} ${TEST_EMAIL}`,
    })
    await expect(accountButton).toBeVisible()

    // Open user menu and check contents
    await accountButton.click()

    const accountPopup = page.getByRole('menu', {
      name: `${initials} ${TEST_FIRSTNAME} ${TEST_LASTNAME} ${TEST_EMAIL}`,
    })
    await expect(accountPopup).toBeVisible()
    // Check if the popup contains the correct user information
    await expect(accountPopup.getByText(`${TEST_FIRSTNAME} ${TEST_LASTNAME}`)).toBeVisible()
    await expect(accountPopup.getByText(TEST_EMAIL)).toBeVisible()
    await expect(accountPopup.getByRole('menuitem', { name: 'Log out' })).toBeVisible()

    // Click outside the menu and check if it's closed
    await page.locator('html').click()
    await expect(accountPopup).not.toBeVisible()

    await accountButton.click()
    await expect(accountPopup).toBeVisible()

    // Press Escape and check if the menu is closed
    await page.locator('html').press('Escape')
    await expect(accountPopup).not.toBeVisible()
  })
})
