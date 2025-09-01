import { expect, test } from '@playwright/test';

test.describe('Page', async () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('has title', async ({ page }) => {
    // Expect a title "to contain" a substring.
    await expect(page).toHaveTitle(/CIVITAS\/Core v2/);
  });

  test('renders the header with a logo and search box', async ({ page }) => {
    const header = page.locator('header');
    await expect(header).toBeVisible();
    await expect(header.getByRole('textbox', { name: 'Search' })).toBeVisible();

    await expect(header.getByRole('link')).toBeVisible();
    await expect(header.getByRole('link')).toHaveAttribute('href', '#');
  });

  test('renders a sidebar with collapsible items', async ({ page }) => {
    await expect(page.getByRole('link', { name: 'Menu item 1' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sub item 1' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sub item 2' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Sub item 3' })).toBeVisible();
    await page
      .getByRole('listitem')
      .filter({ hasText: 'Menu item 1ToggleSub item' })
      .getByRole('button')
      .click();

    await expect(page.getByRole('link', { name: 'Sub item 1' })).not.toBeVisible();
    await expect(page.getByRole('link', { name: 'Sub item 2' })).not.toBeVisible();
    await expect(page.getByRole('link', { name: 'Sub item 3' })).not.toBeVisible();
  });

  test('renders a footer with account information and popup', async ({ page }) => {
    await expect(
      page.locator('div').filter({ hasText: /^CNCivitasmail@example\.com$/ }),
    ).toBeVisible();

    // Open user menu and check contents
    await page.getByRole('button', { name: 'CN Civitas mail@example.com' }).click();
    await expect(page.getByRole('menu', { name: 'CN Civitas mail@example.com' })).toBeVisible();
    await expect(page.getByLabel('CNCivitasmail@example.com').getByText('Civitas')).toBeVisible();
    await expect(
      page.getByLabel('CNCivitasmail@example.com').getByText('mail@example.com'),
    ).toBeVisible();
    await expect(page.getByRole('menuitem', { name: 'Log out' })).toBeVisible();

    // Click outside the menu and check if it's closed
    await page.locator('html').click();
    await expect(page.getByRole('menu', { name: 'CN Civitas mail@example.com' })).not.toBeVisible();

    await page.getByRole('button', { name: 'CN Civitas mail@example.com' }).click();
    await expect(page.getByRole('menu', { name: 'CN Civitas mail@example.com' })).toBeVisible();

    // Press Escape and check if the menu is closed
    await page.locator('html').press('Escape');
    await expect(page.getByRole('menu', { name: 'CN Civitas mail@example.com' })).not.toBeVisible();
  });
});
