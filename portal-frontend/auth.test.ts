import { beforeEach, describe, expect, it, vi } from 'vitest'

// The NextAuth entry point pulls in `next/server`, which does not resolve in the test
// environment; only the provider configuration is under test here.
vi.mock('next-auth', () => ({
  default: vi.fn(() => ({ handlers: {}, auth: vi.fn(), signIn: vi.fn(), signOut: vi.fn() })),
}))

const EXTERNAL_ISSUER = 'https://keycloak.example.com/realms/test'
const INTERNAL_ISSUER = 'http://civitas-keycloak:8080/realms/test'

const loadProvider = async () => {
  vi.resetModules()
  const { keycloakProvider } = await import('./auth')
  return keycloakProvider
}

describe('keycloak provider', () => {
  beforeEach(() => {
    vi.stubEnv('KEYCLOAK_ISSUER', EXTERNAL_ISSUER)
    vi.stubEnv('KEYCLOAK_CLIENT_ID', 'portal-frontend')
    vi.stubEnv('KEYCLOAK_CLIENT_SECRET', 'client-secret')
    vi.stubEnv('KEYCLOAK_INTERNAL_ISSUER', undefined)
  })

  it('keeps the authorization redirect on the external issuer', async () => {
    vi.stubEnv('KEYCLOAK_INTERNAL_ISSUER', INTERNAL_ISSUER)

    const provider = await loadProvider()

    expect(provider.options?.authorization).toMatchObject({
      url: `${EXTERNAL_ISSUER}/protocol/openid-connect/auth`,
    })
  })

  it('pins the server-side endpoints to the internal issuer when one is set', async () => {
    vi.stubEnv('KEYCLOAK_INTERNAL_ISSUER', INTERNAL_ISSUER)

    const provider = await loadProvider()

    expect(provider.options?.token).toBe(`${INTERNAL_ISSUER}/protocol/openid-connect/token`)
    expect(provider.options?.userinfo).toBe(`${INTERNAL_ISSUER}/protocol/openid-connect/userinfo`)
    expect(provider.options?.jwks_endpoint).toBe(`${INTERNAL_ISSUER}/protocol/openid-connect/certs`)
  })

  it('leaves the server-side endpoints to discovery when no internal issuer is set', async () => {
    const provider = await loadProvider()

    expect(provider.options?.token).toBeUndefined()
    expect(provider.options?.userinfo).toBeUndefined()
    expect(provider.options?.jwks_endpoint).toBeUndefined()
  })
})
