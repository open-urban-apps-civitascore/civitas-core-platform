import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { isTokenExpired, refreshAccessToken } from './tokenUtils'

// ─── isTokenExpired ────────────────────────────────────────────────────────────

describe('isTokenExpired', () => {
  // Pinned time: 2000-01-01T00:01:00Z → Date.now() = 946_684_860_000 ms → 946_684_860 s
  const NOW_S = 946_684_860

  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2000-01-01T00:01:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('Returns true when expiresAt is 0', () => {
    expect(isTokenExpired(0)).toBe(true)
  })

  it('Returns true when expiresAt is undefined/falsy', () => {
    expect(isTokenExpired(undefined as unknown as number)).toBe(true)
  })

  it('Returns true when current time is past the 60-second buffer', () => {
    // expiresAt = NOW_S + 60  →  expiryWithBuffer = NOW_S * 1000 = Date.now()  →  expired
    const expiresAt = NOW_S + 60
    expect(isTokenExpired(expiresAt)).toBe(true)
  })

  it('Returns false when token is still valid', () => {
    // expiresAt = NOW_S + 120  →  expiryWithBuffer = (NOW_S + 60) * 1000 > Date.now()  →  valid
    const expiresAt = NOW_S + 120
    expect(isTokenExpired(expiresAt)).toBe(false)
  })
})

// ─── refreshAccessToken ────────────────────────────────────────────────────────

describe('refreshAccessToken', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2000-01-01T00:00:00Z')) // Date.now() = 946_684_800_000 ms → 946_684_800 s
    process.env.KEYCLOAK_ISSUER = 'https://auth.example.com/realms/test'
    process.env.KEYCLOAK_CLIENT_ID = 'test-client'
    process.env.KEYCLOAK_CLIENT_SECRET = 'test-secret'
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('Successful refresh returns new tokens with computed expires_at', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        access_token: 'new-access',
        expires_in: 300,
        refresh_token: 'new-refresh',
      }),
    } as Response)

    const result = await refreshAccessToken('old-refresh')

    expect(result).toEqual({
      access_token: 'new-access',
      // Math.floor(946_684_800 + 300) = 946_685_100
      expires_at: Math.floor(946_684_800 + 300),
      refresh_token: 'new-refresh',
    })
  })

  it('Falls back to old refresh_token when response omits a new one', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({
        access_token: 'new-access',
        expires_in: 300,
        // no refresh_token in response
      }),
    } as Response)

    const result = await refreshAccessToken('old-refresh')

    expect(result.refresh_token).toBe('old-refresh')
  })

  it('returns RefreshTokenError on non-OK HTTP response', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      json: async () => ({ error: 'invalid_grant' }),
    } as Response)

    const result = await refreshAccessToken('old-refresh')

    expect(result).toEqual({
      access_token: '',
      expires_at: 0,
      refresh_token: 'old-refresh',
      error: 'RefreshTokenError',
    })
  })

  it('Network failure returns error sentinel without throwing', async () => {
    vi.spyOn(global, 'fetch').mockRejectedValue(new Error('Network error'))

    await expect(refreshAccessToken('old-refresh')).resolves.toEqual({
      access_token: '',
      expires_at: 0,
      refresh_token: 'old-refresh',
      error: 'RefreshTokenError',
    })
  })

  it('Sends correct request to Keycloak token endpoint', async () => {
    const fetchSpy = vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ access_token: 'a', expires_in: 300, refresh_token: 'r' }),
    } as Response)

    await refreshAccessToken('my-refresh-token')

    expect(fetchSpy).toHaveBeenCalledOnce()
    expect(fetchSpy).toHaveBeenCalledWith(
      'https://auth.example.com/realms/test/protocol/openid-connect/token',
      expect.objectContaining({
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          client_id: 'test-client',
          client_secret: 'test-secret',
          grant_type: 'refresh_token',
          refresh_token: 'my-refresh-token',
        }),
      }),
    )
  })
})
