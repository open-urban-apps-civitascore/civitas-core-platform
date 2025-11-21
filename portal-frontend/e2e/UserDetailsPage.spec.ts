import { expect, test } from '@playwright/test'

import { Authority } from '@/types/users'

const MOCK_USERS = [
  {
    id: '1',
    firstName: 'Mockuser1',
    title: 'female',
    lastName: 'Test',
    email: 'mockuser1.test@test.com',
    authority: null,
    groups: [],
    phone: '+49 75 5576070',
    active: true,
    position: 'Testposition',
    positionDescription: 'Test Description',
    displayName: 'Mockuser1 Test',
  },
]

test.describe('Page', async () => {
  test.beforeEach(async ({ page }) => {
    await page.route('http://localhost:3001/users**', async route => {
      const json = MOCK_USERS
      await route.fulfill({ json })
    })
    await page.route('http://localhost:3001/authorities', async route => {
      const json: Authority[] = []
      await route.fulfill({ json })
    })
    await page.goto('/users')
  })

  test('renders the page elements', async ({ page }) => {
    const pageHeader = page.getByTestId('pageHeader')
    const searchArea = page.getByTestId('searchArea')
    const addUserButton = page.getByTestId('addUserButton')
    const usersTable = page.getByTestId('usersTable')
    const rows = page.getByRole('row')
    await expect(pageHeader).toBeVisible()
    await expect(pageHeader).toContainText('Overview: Users')
    await expect(searchArea).toBeVisible()
    await expect(addUserButton).toBeVisible()
    await expect(usersTable).toBeVisible()
    await expect(rows).toHaveCount(2)
    await expect(rows.nth(1)).toContainText(MOCK_USERS[0].displayName)
  })

  test('create new user page renders the page elements', async ({ page }) => {
    const addUserButton = page.getByTestId('addUserButton')
    await addUserButton.click()
    const createUserPage = page.getByTestId('createUserPage')
    const secondaryTabs = page.getByTestId('secondaryTabs')
    const userDetailsForm = page.getByTestId('userDetailsForm')
    const cancelButton = page.getByTestId('cancelButton')
    const confirmButton = page.getByTestId('confirmButton')
    const editButton = page.getByTestId('editButton')
    const formItem = page.locator('label')
    await expect(createUserPage).toBeVisible()
    await expect(secondaryTabs).toBeVisible()
    await expect(secondaryTabs.locator('button')).toHaveCount(5)
    await expect(secondaryTabs.locator('button').nth(1)).toBeDisabled()
    await expect(userDetailsForm).toBeVisible()
    await expect(editButton).not.toBeVisible()
    await expect(cancelButton).toBeVisible()
    await expect(confirmButton).toBeVisible()
    await expect(formItem).toHaveCount(10)
  })

  test('create new user is working', async ({ page }) => {
    const addUserButton = page.getByTestId('addUserButton')
    await addUserButton.click()

    //select
    const titleSelectTrigger = page.getByTestId('titleSelectTrigger')
    const titleSelectContent = page.getByTestId('titleSelectContent')
    await titleSelectTrigger.click()
    await expect(titleSelectContent).toBeVisible()
    const femaleOption = page.getByTestId('titleSelectItem1')
    await femaleOption.click()

    const firstNameField = page.getByTestId('firstNameTextField')
    await firstNameField.fill('Mockuser2')
    const lastNameField = page.getByTestId('lastNameTextField')
    await lastNameField.fill('Test2')
    const emailField = page.getByTestId('emailTextField')
    await emailField.fill('mockuser2.test2@test.com')

    const confirmButton = page.getByTestId('confirmButton')
    await confirmButton.click()
  })
})
