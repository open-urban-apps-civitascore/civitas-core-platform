import { expect, test } from './base-test'

test.describe('Unauthenticated Access', () => {
  test('redirects to login page when not authenticated', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login/)

    const signInButton = page.getByRole('button', { name: 'Sign in with Keycloak' })
    await expect(signInButton).toBeVisible()
  })

  test('protected API route returns 401 without session', async ({ request }) => {
    const response = await request.get('/api/users', {
      headers: { 'x-api-request': 'true' },
    })
    expect(response.status()).toBe(401)
  })
})
