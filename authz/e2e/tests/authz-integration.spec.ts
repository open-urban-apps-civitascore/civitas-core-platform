/**
 * M5 AuthZ Integration E2E Tests
 *
 * Tests the full authorization flow: Browser -> Next.js BFF -> APISIX -> OPA -> AuthZ Repository
 *
 * Test users (must exist in both Keycloak and database):
 *   - authz.admin@e2e.civitas.dev: Full permissions (DataArchitect role)
 *   - authz.reader@e2e.civitas.dev: Read-only permissions (DataConsumer role)
 *   - authz.none@e2e.civitas.dev: No permissions
 *
 * Prerequisites:
 *   - Full authz stack running (see dev-environment/authz/)
 *   - Test users seeded in Keycloak and database
 *
 * Run: cd dev-environment/authz/e2e && npx playwright test
 */
import { expect, test, Page } from '@playwright/test'

// Test user credentials - configured via environment variables
const AUTHZ_ADMIN_EMAIL = process.env.E2E_AUTHZ_ADMIN_EMAIL || 'authz.admin@e2e.civitas.dev'
const AUTHZ_ADMIN_PASSWORD = process.env.E2E_AUTHZ_ADMIN_PASSWORD || 'test123'
const AUTHZ_READER_EMAIL = process.env.E2E_AUTHZ_READER_EMAIL || 'authz.reader@e2e.civitas.dev'
const AUTHZ_READER_PASSWORD = process.env.E2E_AUTHZ_READER_PASSWORD || 'test123'
const AUTHZ_NOPERMS_EMAIL = process.env.E2E_AUTHZ_NOPERMS_EMAIL || 'authz.none@e2e.civitas.dev'
const AUTHZ_NOPERMS_PASSWORD = process.env.E2E_AUTHZ_NOPERMS_PASSWORD || 'test123'

/**
 * Helper to authenticate via Keycloak login flow
 */
async function authenticateAs(page: Page, email: string, password: string) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()

  await page.getByRole('textbox', { name: 'Username or email' }).fill(email)
  await page.getByRole('textbox', { name: 'Password' }).fill(password)
  await page.getByRole('button', { name: 'Sign In' }).click()

  // Wait for redirect back to app
  await page.waitForURL('/')
}

test.describe('AuthZ Integration - Happy Path', () => {
  test('user with READ_DATASET permission can view datasets page', async ({ page }) => {
    // Login as reader (has READ_DATASET permission)
    await authenticateAs(page, AUTHZ_READER_EMAIL, AUTHZ_READER_PASSWORD)

    // Navigate to datasets page
    await page.goto('/datasets')

    // Should see the datasets page (not an error)
    await expect(page.getByTestId('datasetsPage')).toBeVisible({ timeout: 10000 })

    // Should see the datasets table or list
    const datasetsTable = page.getByTestId('datasetsTable')
    await expect(datasetsTable).toBeVisible()
  })

  test('user with READ_USER permission can view users page', async ({ page }) => {
    // Login as reader (has READ_USER permission)
    await authenticateAs(page, AUTHZ_READER_EMAIL, AUTHZ_READER_PASSWORD)

    // Navigate to users page
    await page.goto('/users')

    // Should see the users page
    await expect(page.getByTestId('usersPage')).toBeVisible({ timeout: 10000 })
    await expect(page.getByTestId('usersTable')).toBeVisible()
  })

  test('admin user can access all protected pages', async ({ page }) => {
    // Login as admin (has all permissions)
    await authenticateAs(page, AUTHZ_ADMIN_EMAIL, AUTHZ_ADMIN_PASSWORD)

    // Navigate to datasets
    await page.goto('/datasets')
    await expect(page.getByTestId('datasetsPage')).toBeVisible({ timeout: 10000 })

    // Navigate to users
    await page.goto('/users')
    await expect(page.getByTestId('usersPage')).toBeVisible({ timeout: 10000 })
  })
})

test.describe('AuthZ Integration - Permission Denied', () => {
  test('user without permissions sees error when accessing protected page', async ({ page }) => {
    // Login as user with no permissions
    await authenticateAs(page, AUTHZ_NOPERMS_EMAIL, AUTHZ_NOPERMS_PASSWORD)

    // Try to access datasets page
    await page.goto('/datasets')

    // Wait for API call and error handling
    await page.waitForTimeout(2000)

    // Check for error indicators or empty state
    const hasError = await page
      .locator('text=/forbidden|unauthorized|permission|access denied|error/i')
      .first()
      .isVisible()
      .catch(() => false)

    const hasEmptyState = await page
      .locator('text=/no .* found|no data|empty/i')
      .first()
      .isVisible()
      .catch(() => false)

    // Either we see an error OR the page shows empty/denied state
    expect(hasError || hasEmptyState).toBe(true)
  })

  test('reader cannot create dataset (403 on POST)', async ({ page, request }) => {
    // Login as reader (has READ but not CREATE permissions)
    await authenticateAs(page, AUTHZ_READER_EMAIL, AUTHZ_READER_PASSWORD)

    // Get the session cookies
    const cookies = await page.context().cookies()

    // Try to create a dataset via API - should return 403
    const response = await request.post('/api/datasets', {
      headers: {
        'Content-Type': 'application/json',
        'x-api-request': 'true',
        Cookie: cookies.map((c) => `${c.name}=${c.value}`).join('; '),
      },
      data: {
        name: 'Test Dataset from E2E',
        description: 'This should fail with 403',
      },
    })

    expect(response.status()).toBe(403)
  })
})

test.describe('AuthZ Integration - Null Permission Endpoints', () => {
  test('any authenticated user can access /users/me', async ({ page, request }) => {
    // Login as user with no permissions
    await authenticateAs(page, AUTHZ_NOPERMS_EMAIL, AUTHZ_NOPERMS_PASSWORD)

    // Get session cookies
    const cookies = await page.context().cookies()

    // Call /users/me API - null-permission endpoint, any authenticated user allowed
    const response = await request.get('/api/users/me', {
      headers: {
        'x-api-request': 'true',
        Cookie: cookies.map((c) => `${c.name}=${c.value}`).join('; '),
      },
    })

    expect(response.status()).toBe(200)
  })
})

test.describe('AuthZ Integration - Unauthenticated', () => {
  test('unauthenticated request to protected endpoint returns 401', async ({ request }) => {
    // No authentication - direct API call without cookies
    const response = await request.get('/api/users', {
      headers: {
        'x-api-request': 'true',
      },
    })

    // Should be unauthorized
    expect(response.status()).toBe(401)
  })
})
