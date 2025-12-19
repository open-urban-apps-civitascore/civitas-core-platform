import { expect, test } from '@playwright/test'

test.describe('Page Layout', async () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('has title', async ({ page }) => {
    // Expect a title "to contain" a substring.
    await expect(page).toHaveTitle(/CIVITAS\/Core v2/)
  })

  test('renders the basic page layout consistently on different pages', async ({ page }) => {
    const header = page.locator('header')
    const sidebar = page.getByLabel('Main navigation')
    const toggleSidebarButton = header.getByRole('button', { name: 'Toggle Sidebar' })
    const breadcrumb = header.getByRole('navigation', { name: 'Breadcrumb' })
    const homeLink = breadcrumb.getByRole('link', { name: 'Home' })
    const languageSelect = header.getByRole('combobox')
    const main = page.locator('main')

    // Check on home page
    await expect(header).toBeVisible()
    await expect(breadcrumb).toBeVisible()
    await expect(main).toBeVisible()
    await expect(sidebar).toBeVisible()
    await expect(homeLink).toBeVisible()
    await expect(homeLink).toHaveAttribute('href', '/')
    await expect(toggleSidebarButton).toBeVisible()
    await expect(languageSelect).toBeVisible()

    // Check on datasets page
    await page.goto('/datasets')
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/\/datasets/)

    await expect(header).toBeVisible()
    await expect(breadcrumb).toBeVisible()
    await expect(main).toBeVisible()
    await expect(sidebar).toBeVisible()
    await expect(homeLink).toBeVisible()
    await expect(homeLink).toHaveAttribute('href', '/')
    await expect(toggleSidebarButton).toBeVisible()
    await expect(languageSelect).toBeVisible()
  })

  test('toggles the sidebar visibility when clicking the toggle button', async ({ page }) => {
    const sidebar = page.getByLabel('Main navigation')
    await expect(sidebar).toBeVisible()

    const toggleSidebarButton = page.getByRole('button', { name: 'Toggle Sidebar' })
    await expect(toggleSidebarButton).toBeVisible()

    // Check if the sidebar is initially expanded
    const navItem = page.getByText('Our Data')

    // Click the toggle button to hide the sidebar
    await toggleSidebarButton.click()
    await expect(navItem).not.toBeVisible()

    // Click the toggle button again to show the sidebar
    await toggleSidebarButton.click()
    await expect(navItem).toBeVisible()
  })

  test('navigates to the correct page when clicking sidebar links', async ({ page }) => {
    const sidebar = page.getByLabel('Main navigation')
    await expect(sidebar).toBeVisible()

    // Click the "Our Data" link and check if URL and main content area are correct
    const dataLink = sidebar.getByRole('link', { name: 'Our Data' })
    await expect(dataLink).toBeVisible()
    await dataLink.click()
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/datasets/)
    const heading = page.getByRole('heading', { name: 'Datasets' })
    await expect(heading).toBeVisible()

    // Click "User Management" link and check if URL and main content area are correct
    const userManagementLink = sidebar.getByRole('link', { name: 'User Management' })
    await expect(userManagementLink).toBeVisible()
    await userManagementLink.click()
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/users/)
    const userManagementHeading = page.getByRole('heading', { name: 'Overview: Users' })
    await expect(userManagementHeading).toBeVisible()
  })

  test('updates breadcrumb correctly', async ({ page }) => {
    await page.goto('/datasets')
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/\/datasets/)

    // Check breadcrumb contents on datasets page
    const breadcrumb = page.getByRole('navigation', { name: 'Breadcrumb' })
    const homePageLink = breadcrumb.getByRole('link', { name: 'Home' })
    const datasetsPageLink = breadcrumb.getByText('Our Data')

    await expect(homePageLink).toBeVisible()
    await expect(datasetsPageLink).toBeVisible()
    await expect(datasetsPageLink).toHaveAttribute('aria-current', 'page')
    await expect(homePageLink).not.toHaveAttribute('aria-current', 'page')

    // Click the "Home" link in the breadcrumb and check if the URL is correct
    await homePageLink.click()
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/\/$/)

    await page.goto('/users')
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/\/users/)

    // Check breadcrumb contents on users page
    const breadcrumbUsers = page.getByRole('navigation', { name: 'Breadcrumb' })
    const homePageLinkUsers = breadcrumbUsers.getByRole('link', { name: 'Home' })
    const usersPageLink = breadcrumbUsers.getByText('User Management')

    await expect(homePageLinkUsers).toBeVisible()
    await expect(usersPageLink).toBeVisible()
    await expect(usersPageLink).toHaveAttribute('aria-current', 'page')
    await expect(homePageLinkUsers).not.toHaveAttribute('aria-current', 'page')

    // Click the "Home" link in the breadcrumb and check if the URL is correct
    await homePageLinkUsers.click()
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/\/$/)
  })
})

test.describe('Language Switcher', () => {
  const languageOptionsEnglish = { german: 'German', english: 'English' }
  const languageOptionsGerman = { german: 'Deutsch', english: 'Englisch' }

  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('language select works correctly', async ({ page }) => {
    const header = page.locator('header')
    const languageSelect = header.getByRole('combobox')
    await expect(languageSelect).toBeVisible()

    // Open language select and check contents
    await languageSelect.click()
    const languageMenu = page.getByRole('listbox')
    await expect(languageMenu).toBeVisible()

    // Check if ainitially the options are shown in English
    for (const option of Object.values(languageOptionsEnglish)) {
      await expect(languageMenu.getByRole('option', { name: option })).toBeVisible()
    }

    // Select German and check if language is updated
    await languageMenu.getByRole('option', { name: languageOptionsEnglish['german'] }).click()
    await expect(languageMenu).not.toBeVisible()
    await expect(page.getByText('Unsere Daten')).toBeVisible()

    // Open language select again
    await languageSelect.click()
    await expect(languageMenu).toBeVisible()

    // Check if the options are shown in German
    for (const option of Object.values(languageOptionsGerman)) {
      await expect(languageMenu.getByRole('option', { name: option })).toBeVisible()
    }

    // Select English and check if language is updated
    await languageMenu.getByRole('option', { name: languageOptionsGerman['english'] }).click()
    await expect(languageMenu).not.toBeVisible()
    await expect(page.getByText('Our Data')).toBeVisible()

    //Check if language menu is closed when clicking outside
    await languageSelect.click()
    await expect(languageMenu).toBeVisible()
    await page.locator('html').click()
    await expect(languageMenu).not.toBeVisible()

    //Check if language menu is closed when pressing Escape
    await languageSelect.click()
    await expect(languageMenu).toBeVisible()
    await page.locator('html').press('Escape')
    await expect(languageMenu).not.toBeVisible()
  })
})
