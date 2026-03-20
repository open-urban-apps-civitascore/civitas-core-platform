import { beforeEach, describe, expect, it, vi } from 'vitest'

import { authConfig } from './auth.config'
import { isTokenExpired, refreshAccessToken } from './src/lib/tokenUtils'

vi.mock('./src/lib/tokenUtils', () => ({
  isTokenExpired: vi.fn(),
  refreshAccessToken: vi.fn(),
}))

const mockIsTokenExpired = vi.mocked(isTokenExpired)
const mockRefreshAccessToken = vi.mocked(refreshAccessToken)

const jwtCallback = authConfig.callbacks!.jwt!
const authorizedCallback = authConfig.callbacks!.authorized!
const sessionCallback = authConfig.callbacks!.session!
const signOutEvent = authConfig.events!.signOut!

describe('authConfig callbacks', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('jwt callback', () => {
    it('First login (account present) stores tokens correctly', async () => {
      const token = { sub: 'user-id' }
      const account = {
        access_token: 'access-123',
        expires_at: 9999999,
        refresh_token: 'refresh-123',
        id_token: 'id-123',
      }

      const result = await jwtCallback({ token, account } as unknown as Parameters<typeof jwtCallback>[0])

      expect(result).toMatchObject({
        sub: 'user-id',
        access_token: 'access-123',
        expires_at: 9999999,
        refresh_token: 'refresh-123',
        id_token: 'id-123',
        refresh_attempts: 0,
      })
    })

    it('Valid token resets refresh_attempts to 0', async () => {
      mockIsTokenExpired.mockReturnValue(false)

      const token = {
        sub: 'user-id',
        access_token: 'access-123',
        expires_at: 9999999,
        refresh_attempts: 2,
      }

      const result = await jwtCallback({ token, account: null } as unknown as Parameters<typeof jwtCallback>[0])

      expect(result).toMatchObject({ ...token, refresh_attempts: 0 })
      expect(mockIsTokenExpired).toHaveBeenCalledWith(token.expires_at)
    })

    it('Expired token triggers refreshAccessToken', async () => {
      mockIsTokenExpired.mockReturnValue(true)
      mockRefreshAccessToken.mockResolvedValue({
        access_token: 'new-access-123',
        expires_at: 8888888,
        refresh_token: 'new-refresh-123',
      })

      const token = {
        sub: 'user-id',
        access_token: 'old-access',
        expires_at: 1111111,
        refresh_token: 'refresh-123',
        refresh_attempts: 0,
      }

      const result = await jwtCallback({ token, account: null } as unknown as Parameters<typeof jwtCallback>[0])

      expect(mockRefreshAccessToken).toHaveBeenCalledWith('refresh-123')
      expect(result).toMatchObject({
        access_token: 'new-access-123',
        expires_at: 8888888,
        refresh_token: 'new-refresh-123',
        refresh_attempts: 0,
      })
    })

    it('Refresh failure increments refresh_attempts', async () => {
      mockIsTokenExpired.mockReturnValue(true)
      mockRefreshAccessToken.mockResolvedValue({
        access_token: '',
        expires_at: 0,
        refresh_token: 'refresh-123',
        error: 'RefreshTokenError',
      })

      const token = {
        sub: 'user-id',
        refresh_token: 'refresh-123',
        refresh_attempts: 1,
      }

      const result = await jwtCallback({ token, account: null } as unknown as Parameters<typeof jwtCallback>[0])

      expect(result).toMatchObject({
        refresh_attempts: 2,
        error: 'RefreshTokenError',
      })
    })

    it('returns null after 3 failed refresh attempts (force logout)', async () => {
      mockIsTokenExpired.mockReturnValue(true)

      const token = {
        sub: 'user-id',
        refresh_token: 'refresh-123',
        refresh_attempts: 3,
      }

      const result = await jwtCallback({ token, account: null } as unknown as Parameters<typeof jwtCallback>[0])

      expect(result).toBeNull()
      expect(mockRefreshAccessToken).not.toHaveBeenCalled()
    })

    it('Missing refresh_token returns error', async () => {
      mockIsTokenExpired.mockReturnValue(true)

      const token = {
        sub: 'user-id',
        refresh_token: undefined,
        refresh_attempts: 0,
      }

      const result = await jwtCallback({ token, account: null } as unknown as Parameters<typeof jwtCallback>[0])

      expect(result).toMatchObject({ error: 'RefreshTokenError' })
      expect(mockRefreshAccessToken).not.toHaveBeenCalled()
    })

    it('undefined refresh_attempts defaults to 0 on refresh failure', async () => {
      mockIsTokenExpired.mockReturnValue(true)
      mockRefreshAccessToken.mockResolvedValue({
        access_token: '',
        expires_at: 0,
        refresh_token: 'refresh-123',
        error: 'RefreshTokenError',
      })

      const token = {
        sub: 'user-id',
        refresh_token: 'refresh-123',
        // refresh_attempts intentionally absent to test the || 0 fallback
      }

      const result = await jwtCallback({ token, account: null } as unknown as Parameters<typeof jwtCallback>[0])

      expect(result).toMatchObject({ refresh_attempts: 1, error: 'RefreshTokenError' })
    })
  })

  describe('authorized callback', () => {
    it('Public paths (/login, /api/auth/*) return true', () => {
      const loginResult = authorizedCallback({
        auth: null,
        request: { nextUrl: new URL('http://localhost/login') },
      } as Parameters<typeof authorizedCallback>[0])

      const apiAuthResult = authorizedCallback({
        auth: null,
        request: { nextUrl: new URL('http://localhost/api/auth/callback/keycloak') },
      } as Parameters<typeof authorizedCallback>[0])

      expect(loginResult).toBe(true)
      expect(apiAuthResult).toBe(true)
    })

    it('Authenticated users on protected paths return true', () => {
      const result = authorizedCallback({
        auth: { user: { name: 'Test User' }, expires: '9999' },
        request: { nextUrl: new URL('http://localhost/dashboard') },
      } as Parameters<typeof authorizedCallback>[0])

      expect(result).toBe(true)
    })

    it('Unauthenticated users on protected paths return false', () => {
      const result = authorizedCallback({
        auth: null,
        request: { nextUrl: new URL('http://localhost/dashboard') },
      } as Parameters<typeof authorizedCallback>[0])

      expect(result).toBe(false)
    })
  })

  describe('session callback', () => {
    it('Adds error from token to session', () => {
      const session = { user: { name: 'Test User' }, expires: '9999' }
      const token = { error: 'RefreshTokenError' }

      const result = sessionCallback({ session, token } as unknown as Parameters<typeof sessionCallback>[0])

      expect(result).toMatchObject({ ...session, error: 'RefreshTokenError' })
    })

    it('omits error from session when token has no error', () => {
      const session = { user: { name: 'Test User' }, expires: '9999' }
      const token = {}

      const result = sessionCallback({ session, token } as unknown as Parameters<typeof sessionCallback>[0])

      expect(result).toMatchObject({ ...session, error: undefined })
    })
  })

  describe('events.signOut', () => {
    const KEYCLOAK_ISSUER = 'https://keycloak.example.com/realms/test'
    const KEYCLOAK_CLIENT_ID = 'my-client'

    beforeEach(() => {
      vi.spyOn(console, 'error').mockImplementation(() => {})
      vi.spyOn(console, 'log').mockImplementation(() => {})
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
    })

    afterEach(() => {
      vi.unstubAllEnvs()
      vi.unstubAllGlobals()
    })

    it('does not call fetch and logs console.error when KEYCLOAK_ISSUER is missing', async () => {
      vi.stubEnv('KEYCLOAK_ISSUER', '')

      await signOutEvent({ token: { id_token: 'id-123' } })

      expect(fetch).not.toHaveBeenCalled()
      expect(console.error).toHaveBeenCalledWith('KEYCLOAK_ISSUER environment variable is not set')
    })

    it('includes id_token_hint in logout URL when token has id_token', async () => {
      vi.stubEnv('KEYCLOAK_ISSUER', KEYCLOAK_ISSUER)
      vi.stubEnv('KEYCLOAK_CLIENT_ID', KEYCLOAK_CLIENT_ID)

      await signOutEvent({ token: { id_token: 'my-id-token' } })

      const calledUrl = vi.mocked(fetch).mock.calls[0][0] as string
      const url = new URL(calledUrl)
      expect(url.searchParams.get('id_token_hint')).toBe('my-id-token')
      expect(url.searchParams.get('client_id')).toBe(KEYCLOAK_CLIENT_ID)
    })

    it('does not include id_token_hint in logout URL when token has no id_token', async () => {
      vi.stubEnv('KEYCLOAK_ISSUER', KEYCLOAK_ISSUER)
      vi.stubEnv('KEYCLOAK_CLIENT_ID', KEYCLOAK_CLIENT_ID)

      await signOutEvent({ token: { sub: 'user-id' } })

      const calledUrl = vi.mocked(fetch).mock.calls[0][0] as string
      const url = new URL(calledUrl)
      expect(url.searchParams.has('id_token_hint')).toBe(false)
    })

    it('does not include id_token_hint in logout URL when token is null', async () => {
      vi.stubEnv('KEYCLOAK_ISSUER', KEYCLOAK_ISSUER)
      vi.stubEnv('KEYCLOAK_CLIENT_ID', KEYCLOAK_CLIENT_ID)

      await signOutEvent({ token: null })

      const calledUrl = vi.mocked(fetch).mock.calls[0][0] as string
      const url = new URL(calledUrl)
      expect(url.searchParams.has('id_token_hint')).toBe(false)
    })

    it('does not throw and logs console.error when fetch fails', async () => {
      vi.stubEnv('KEYCLOAK_ISSUER', KEYCLOAK_ISSUER)
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')))

      await expect(signOutEvent({ token: { id_token: 'id-123' } })).resolves.not.toThrow()
      expect(console.error).toHaveBeenCalledWith('Error during Keycloak logout:', expect.any(Error))
    })
  })
})
