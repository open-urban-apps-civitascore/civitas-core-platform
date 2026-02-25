/**
 * OAuth Flow Validation Test
 *
 * This test validates the complete OAuth flow WITHOUT using cached authentication state.
 * It catches configuration issues like missing AUTH_SECRET that would otherwise be masked
 * by session caching.
 *
 * IMPORTANT: This test uses a fresh browser context (no storageState) to ensure the
 * full OAuth flow is tested every time.
 *
 * Moved from portal-frontend/e2e/auth/ to authz/e2e/ to keep all our E2E tests together.
 */
import { test, expect } from '@playwright/test'

// Override the default test to NOT use stored auth state
// Use desktop viewport since sidebar is hidden on mobile (md:block)
test.use({
  storageState: { cookies: [], origins: [] },
  viewport: { width: 1280, height: 720 },
})

// Use authz admin credentials (works for OAuth flow validation too)
const TEST_EMAIL = process.env.E2E_AUTHZ_ADMIN_EMAIL || 'authz.admin@e2e.civitas.dev'
const TEST_PASSWORD = process.env.E2E_AUTHZ_ADMIN_PASSWORD || 'test123'

test.describe('OAuth Flow Validation', () => {
  test('complete OAuth flow works without cached state', async ({ page }) => {
    // Start from the app root - should redirect to login
    await page.goto('/')

    // Should be redirected to login page
    await expect(page).toHaveURL('/login', { timeout: 10000 })

    // Click the Keycloak sign-in button
    await page.getByRole('button', { name: /sign in with keycloak/i }).click()

    // Should be on Keycloak login page
    await expect(page.getByRole('textbox', { name: /username or email/i })).toBeVisible({
      timeout: 10000,
    })

    // Enter credentials
    await page.getByRole('textbox', { name: /username or email/i }).fill(TEST_EMAIL)
    await page.getByRole('textbox', { name: /password/i }).fill(TEST_PASSWORD)
    await page.getByRole('button', { name: /sign in/i }).click()

    // Critical: This is where AUTH_SECRET errors would manifest as "Server error"
    // If we see "Server error" or stay on an error page, the OAuth config is broken

    // Wait for the OAuth callback to complete - either success (/) or error
    await page.waitForURL((url) => !url.href.includes('localhost:8080'), { timeout: 15000 })

    // Check for OAuth configuration errors (the issue we're trying to catch)
    const pageContent = await page.textContent('body')
    const hasServerError =
      pageContent?.includes('Server error') ||
      pageContent?.includes('There is a problem with the server configuration')

    if (hasServerError) {
      throw new Error(
        'OAuth configuration error detected! Check AUTH_SECRET and other NextAuth environment variables.',
      )
    }

    // Should have been redirected to the app (not stuck on error page)
    await expect(page).toHaveURL('/', { timeout: 5000 })

    // Verify we're logged in by checking for auth session
    await expect(page.locator('main')).toBeVisible({ timeout: 10000 })
  })

  test('auth providers endpoint returns Keycloak config', async ({ request }) => {
    // This validates the NextAuth configuration is loaded correctly
    const response = await request.get('/api/auth/providers')
    expect(response.status()).toBe(200)

    const providers = await response.json()
    expect(providers).toHaveProperty('keycloak')
    expect(providers.keycloak).toMatchObject({
      id: 'keycloak',
      type: 'oidc',
    })
  })

  test('CSRF token endpoint works', async ({ request }) => {
    // NextAuth requires a working CSRF token endpoint
    const response = await request.get('/api/auth/csrf')
    expect(response.status()).toBe(200)

    const data = await response.json()
    expect(data).toHaveProperty('csrfToken')
    expect(data.csrfToken).toBeTruthy()
  })
})
