import { expect, test } from '@playwright/test'

test.describe('Page Layout', async () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
  })

  test('has title', async ({ page }) => {
    // Expect a title "to contain" a substring.
    await expect(page).toHaveTitle(/CIVITAS\/CORE V2/)
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
    const sidebar = page.locator('[data-slot="sidebar"]')
    await expect(sidebar).toHaveAttribute('data-state', 'expanded')

    const toggleSidebarButton = page.getByRole('button', { name: 'Toggle Sidebar' })
    await expect(toggleSidebarButton).toBeVisible()

    // The labels stay in the DOM when the sidebar collapses to its icons, so the state is checked
    await toggleSidebarButton.click()
    await expect(sidebar).toHaveAttribute('data-state', 'collapsed')

    await toggleSidebarButton.click()
    await expect(sidebar).toHaveAttribute('data-state', 'expanded')
  })

  test('navigates to the correct page when clicking sidebar links', async ({ page }) => {
    const sidebar = page.getByLabel('Main navigation')
    await expect(sidebar).toBeVisible()

    // "Our data" is a group: its links are Data pools, Datasets, Data sources and Data structures
    const datasetsLink = sidebar.getByRole('link', { name: 'Datasets', exact: true })
    await expect(datasetsLink).toBeVisible()
    await datasetsLink.click()
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/datasets/)
    await expect(page.getByRole('heading', { name: 'Datasets', level: 1 })).toBeVisible()

    // "Tenant management" is a collapsed group with the user management inside
    await sidebar.getByRole('button', { name: 'Tenant management' }).click()
    const usersLink = sidebar.getByRole('link', { name: 'User', exact: true })
    await expect(usersLink).toBeVisible()
    await usersLink.click()
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveURL(/\/users/)
    await expect(page.getByRole('heading', { name: 'Overview: Users' })).toBeVisible()
  })

  test('updates breadcrumb correctly', async ({ page }) => {
    await page.goto('/datasets')
    await page.waitForLoadState('networkidle')
    await expect(page).toHaveURL(/\/datasets/)

    // Breadcrumb on the datasets page: Home / Datasets
    const breadcrumb = page.getByRole('navigation', { name: 'Breadcrumb' })
    const homePageLink = breadcrumb.getByRole('link', { name: 'Home' })
    const datasetsPageLink = breadcrumb.getByRole('link', { name: 'Datasets' })

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

    const breadcrumbUsers = page.getByRole('navigation', { name: 'Breadcrumb' })
    const homePageLinkUsers = breadcrumbUsers.getByRole('link', { name: 'Home' })
    const usersPageLink = breadcrumbUsers.getByRole('link', { name: /users?/i })

    await expect(homePageLinkUsers).toBeVisible()
    await expect(usersPageLink).toBeVisible()
    await expect(usersPageLink).toHaveAttribute('aria-current', 'page')
    await expect(homePageLinkUsers).not.toHaveAttribute('aria-current', 'page')

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
    await expect(page.getByText('Unsere Daten', { exact: true })).toBeVisible()

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
    await expect(page.getByText('Our data', { exact: true })).toBeVisible()

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
