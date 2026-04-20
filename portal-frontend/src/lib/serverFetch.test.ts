import type { ReadonlyHeaders } from 'next/dist/server/web/spec-extension/adapters/headers'
import { headers as nextHeaders } from 'next/headers'
import { getToken, type JWT } from 'next-auth/jwt'
import { MockInstance } from 'vitest'

import { AuthError, serverFetch } from './serverFetch'

vi.mock('next/headers', () => ({
  headers: vi.fn(),
}))

vi.mock('next-auth/jwt', () => ({
  getToken: vi.fn(),
}))

const mockedNextHeaders = vi.mocked(nextHeaders)
const mockedGetToken = vi.mocked(getToken)

const makeHeaders = (entries: Record<string, string | null> = {}): Pick<ReadonlyHeaders, 'get'> => ({
  get: (key: string) => entries[key] ?? null,
})

describe('serverFetch', () => {
  let fetchSpy: MockInstance

  beforeEach(() => {
    vi.resetAllMocks()

    mockedNextHeaders.mockResolvedValue(makeHeaders() as unknown as ReadonlyHeaders)
    mockedGetToken.mockResolvedValue({ access_token: 'test-token' } as JWT)

    fetchSpy = vi.spyOn(global, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => ({ id: 1 }),
    } as Response)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    delete process.env.API_BASE_URL
    delete process.env.API_PORT
  })

  describe('Authorization and headers', () => {
    it('throws AuthError "Not authenticated" when no token is available', async () => {
      mockedGetToken.mockResolvedValue(null)

      const promise = serverFetch({ endpoint: '/test' })
      await expect(promise).rejects.toThrow(AuthError)
      await expect(promise).rejects.toThrow('Not authenticated')
    })

    it('throws AuthError "No access token available" when token exists but has no access_token', async () => {
      mockedGetToken.mockResolvedValue({} as JWT)

      const promise = serverFetch({ endpoint: '/test' })

      await expect(promise).rejects.toThrow(AuthError)
      await expect(promise).rejects.toThrow('No access token available')
    })

    it('throws "Backend URL not configured" when API env vars are missing for API backend', async () => {
      await expect(serverFetch({ endpoint: '/test', isApiBackend: true })).rejects.toThrow('Backend URL not configured')
    })

    it('sets Authorization: Bearer <token> header', async () => {
      await serverFetch({ endpoint: '/test' })

      expect(fetchSpy).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer test-token',
          }),
        }),
      )
    })

    it('merges custom headers with default headers', async () => {
      await serverFetch({ endpoint: '/test', headers: { 'X-Custom-Header': 'custom-value' } })

      expect(fetchSpy).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer test-token',
            'Content-Type': 'application/json',
            'X-Custom-Header': 'custom-value',
          }),
        }),
      )
    })

    it('allows custom headers to override default headers', async () => {
      await serverFetch({ endpoint: '/test', headers: { 'Content-Type': 'text/plain' } })

      expect(fetchSpy).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          headers: expect.objectContaining({
            'Content-Type': 'text/plain',
          }),
        }),
      )
    })
  })

  describe('Correctly detects secure cookie', () => {
    it.each([
      ['x-forwarded-scheme', 'https'],
      ['x-scheme', 'https'],
      ['x-forwarded-proto', 'https'],
    ])('marks secureCookie: true when %s header is "https"', async (headerName, headerValue) => {
      mockedNextHeaders.mockResolvedValue(makeHeaders({ [headerName]: headerValue }) as unknown as ReadonlyHeaders)

      await serverFetch({ endpoint: '/test' })

      expect(mockedGetToken).toHaveBeenCalledWith(expect.objectContaining({ secureCookie: true }))
    })

    it('marks secureCookie: false when no forwarded proto header is present', async () => {
      mockedNextHeaders.mockResolvedValue(makeHeaders() as unknown as ReadonlyHeaders)

      await serverFetch({ endpoint: '/test' })

      expect(mockedGetToken).toHaveBeenCalledWith(expect.objectContaining({ secureCookie: false }))
    })

    it('marks secureCookie: false when forwarded proto header is "http"', async () => {
      mockedNextHeaders.mockResolvedValue(makeHeaders({ 'x-forwarded-proto': 'http' }) as never)

      await serverFetch({ endpoint: '/test' })

      expect(mockedGetToken).toHaveBeenCalledWith(expect.objectContaining({ secureCookie: false }))
    })
  })

  describe('Normalizes response', () => {
    it('returns content and pagination metadata when response already has a content field', async () => {
      fetchSpy.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        json: async () => ({ content: { id: 42 }, totalElements: 5, totalPages: 2 }),
      } as Response)

      const result = await serverFetch<{ id: number }>({ endpoint: '/test' })

      expect(result).toEqual({ data: { id: 42 }, totalElements: 5, totalPages: 2 })
    })

    it('wraps object response in content when no content field is present', async () => {
      fetchSpy.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        json: async () => ({ id: 42 }),
      } as Response)

      const result = await serverFetch<{ id: number }>({ endpoint: '/test' })

      expect(result.data).toEqual({ id: 42 })
    })

    it('reads x-total-count header for array responses', async () => {
      fetchSpy.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'x-total-count': '100' }),
        json: async () => [{ id: 1 }, { id: 2 }],
      } as Response)

      const result = await serverFetch<{ id: number }[]>({ endpoint: '/test' })

      expect(result.totalElements).toBe(100)
    })

    it('sets totalElements to 0 when array response has no x-total-count header', async () => {
      fetchSpy.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        json: async () => [{ id: 1 }],
      } as Response)

      const result = await serverFetch<{ id: number }[]>({ endpoint: '/test' })

      expect(result.totalElements).toBe(0)
    })
  })

  describe('Routes to correct backend based on isApiBackend flag', () => {
    it('uses JSON Server URL when isApiBackend is false (default)', async () => {
      await serverFetch({ endpoint: '/resources' })

      expect(fetchSpy).toHaveBeenCalledWith('http://localhost:3001/resources', expect.any(Object))
    })

    it('uses API backend URL when isApiBackend is true', async () => {
      process.env.API_BASE_URL = 'http://api.example.com'
      process.env.API_PORT = '8080'

      await serverFetch({ endpoint: '/resources', isApiBackend: true })

      expect(fetchSpy).toHaveBeenCalledWith('http://api.example.com:8080/v1/resources', expect.any(Object))
    })

    it('appends query params to the URL', async () => {
      await serverFetch({ endpoint: '/resources', params: new URLSearchParams({ page: '2' }) })

      expect(fetchSpy).toHaveBeenCalledWith('http://localhost:3001/resources?page=2', expect.any(Object))
    })
  })

  describe('Includes body only for POST/PUT/PATCH methods', () => {
    const body = { name: 'payload' }

    it.each(['POST', 'PUT', 'PATCH'] as const)('serializes body as JSON for %s request', async method => {
      await serverFetch({ endpoint: '/test', method, body })

      expect(fetchSpy).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ body: JSON.stringify(body) }))
    })

    it.each(['GET', 'DELETE'] as const)('omits body for %s request', async method => {
      await serverFetch({ endpoint: '/test', method, body })

      const [, options] = fetchSpy.mock.calls[0]
      expect((options as RequestInit).body).toBeUndefined()
    })
  })

  it('throws AuthError on 401 response from backend', async () => {
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers(),
    } as Response)

    const promise = serverFetch({ endpoint: '/test' })
    await expect(promise).rejects.toThrow(AuthError)
    await expect(promise).rejects.toThrow('Backend responded with status 401')
  })

  it('throws generic Error on non-401 error response', async () => {
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 500,
      headers: new Headers(),
    } as Response)

    await expect(serverFetch({ endpoint: '/test' })).rejects.toThrow('Backend responded with status 500')
  })
})
