import { expect, test } from '@playwright/test'

import { UserResponse } from '@/types/users'

import { createTestUser } from '../../playwright/createTestUser'
import { removeTestUser } from '../../playwright/removeTestUser'

test.describe('User List', async () => {
  let user: UserResponse

  test.beforeEach(async ({ page }) => {
    user = await createTestUser()
    await page.goto('/users')
  })

  test.afterEach(async () => {
    await removeTestUser(user.id)
  })

  test('renders the page elements', async ({ page }) => {
    await page.getByTestId('loadingSkeleton').waitFor({ state: 'hidden' })

    const pageHeader = page.getByTestId('pageHeader')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('Overview: Users')
    await expect(page.getByTestId('addUserButton')).toBeVisible()

    await expect(page.getByTestId('usersTable')).toBeVisible()

    const searchArea = page.getByTestId('searchArea')
    await expect(searchArea).toBeVisible()
    searchArea.locator('input').fill(user.firstName)

    const rows = page.getByRole('row')
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText(user.displayName)

    searchArea.locator('input').fill(crypto.randomUUID())
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText('No results found.')
  })

  test('navigates to create user page', async ({ page }) => {
    const addUserButton = page.getByTestId('addUserButton')
    await addUserButton.click()
    const createUserPage = page.getByTestId('createUserPage')
    await expect(createUserPage).toBeVisible()
  })

  test('navigates to user details page', async ({ page }) => {
    await page.getByTestId('loadingSkeleton').waitFor({ state: 'hidden' })
    await page.getByTestId('searchArea').locator('input').fill(user.firstName)

    const userRow = page.getByRole('row').filter({ hasText: user.displayName })
    await userRow.click()
    await expect(page.getByTestId('userDetailsPage')).toBeVisible()
    await expect(page.getByTestId('pageHeader')).toContainText(user.displayName)
  })
})
