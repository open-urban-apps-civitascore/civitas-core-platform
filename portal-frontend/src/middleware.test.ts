import { NextRequest, NextResponse } from 'next/server'
import { beforeEach, describe, expect, it, vi } from 'vitest'

// Capture the middleware callback passed to auth()
let middlewareCallback: (req: NextRequest & { auth?: { user: object } | null }) => NextResponse

vi.mock('../auth', () => ({
  auth: vi.fn((callback: typeof middlewareCallback) => {
    middlewareCallback = callback
  }),
}))

vi.mock('next/server', async importOriginal => {
  const actual = await importOriginal<typeof import('next/server')>()
  return {
    ...actual,
    NextResponse: {
      ...actual.NextResponse,
      redirect: vi.fn(url => ({
        type: 'redirect',
        url,
        headers: new Headers(),
      })),
      next: vi.fn(() => ({
        type: 'next',
        headers: new Headers(),
      })),
    },
  }
})

// Import after mocks are set up so auth() is called with our spy
await import('./middleware')

const buildRequest = (
  pathname: string,
  auth?: { user: object } | null,
): NextRequest & { auth?: { user: object } | null } => {
  const req = new NextRequest(new URL(`http://localhost:3000${pathname}`)) as NextRequest & {
    auth?: { user: object } | null
  }
  req.auth = auth
  return req
}

describe('middleware', () => {
  beforeEach(() => {
    vi.unstubAllEnvs()
    vi.mocked(NextResponse.redirect).mockClear()
  })

  describe('authentication redirect', () => {
    it('redirects unauthenticated request to a non-login path to /login', () => {
      const req = buildRequest('/dashboard', null)
      const result = middlewareCallback(req)

      expect(result?.type).toBe('redirect')
      expect(result?.url).toEqual(new URL('/login', 'http://localhost:3000'))
    })

    it('does not redirect when already on /login path without auth', () => {
      const req = buildRequest('/login', null)
      const result = middlewareCallback(req)

      expect(NextResponse.redirect).not.toHaveBeenCalled()
      expect(result?.type).toBe('next')
    })

    it('passes through authenticated request without redirecting', () => {
      const req = buildRequest('/dashboard', { user: { name: 'Alice' } })
      const result = middlewareCallback(req)

      expect(NextResponse.redirect).not.toHaveBeenCalled()
      expect(result?.type).toBe('next')
    })
  })

  describe('security headers on authenticated requests', () => {
    let result: ReturnType<typeof middlewareCallback>

    beforeEach(() => {
      const req = buildRequest('/dashboard', { user: { name: 'Alice' } })
      result = middlewareCallback(req)
    })

    it('sets X-Frame-Options to DENY', () => {
      expect(result?.headers.get('X-Frame-Options')).toBe('DENY')
    })

    it('sets X-Content-Type-Options to nosniff', () => {
      expect(result?.headers.get('X-Content-Type-Options')).toBe('nosniff')
    })

    it('sets Referrer-Policy to strict-origin-when-cross-origin', () => {
      expect(result?.headers.get('Referrer-Policy')).toBe('strict-origin-when-cross-origin')
    })

    it('sets x-script-nonce header', () => {
      expect(result?.headers.get('x-script-nonce')).toBeTruthy()
    })

    it('sets x-style-nonce header', () => {
      expect(result?.headers.get('x-style-nonce')).toBeTruthy()
    })
  })

  describe('nonce values', () => {
    it('nonces are non-empty base64 strings', () => {
      const req = buildRequest('/page', { user: { name: 'Alice' } })
      const result = middlewareCallback(req)
      const scriptNonce = result?.headers.get('x-script-nonce')
      const styleNonce = result?.headers.get('x-style-nonce')

      expect(scriptNonce).toMatch(/^[A-Za-z0-9+/]+=*$/)
      expect(styleNonce).toMatch(/^[A-Za-z0-9+/]+=*$/)
    })

    it('generates unique nonces per request', () => {
      const req1 = buildRequest('/page', { user: { name: 'Alice' } })
      const req2 = buildRequest('/page', { user: { name: 'Alice' } })

      const nonce1 = middlewareCallback(req1)?.headers.get('x-script-nonce')
      const nonce2 = middlewareCallback(req2)?.headers.get('x-script-nonce')

      expect(nonce1).not.toBe(nonce2)
    })
  })

  describe('CSP header in production mode', () => {
    let csp: string
    let result: ReturnType<typeof middlewareCallback>

    beforeEach(() => {
      vi.stubEnv('NODE_ENV', 'production')
      const req = buildRequest('/dashboard', { user: { name: 'Alice' } })
      result = middlewareCallback(req)
      csp = result?.headers.get('Content-Security-Policy') ?? ''
    })

    it('contains the script nonce', () => {
      expect(csp).toMatch(/script-src[^;]*'nonce-[A-Za-z0-9+/]+=*'/)
    })

    it('does not include unsafe-inline in script-src', () => {
      const scriptSrc = csp.split(';').find(d => d.trim().startsWith('script-src'))
      expect(scriptSrc).not.toContain("'unsafe-inline'")
    })

    it('includes upgrade-insecure-requests', () => {
      expect(csp).toContain('upgrade-insecure-requests')
    })

    it('blocks object-src', () => {
      expect(csp).toContain("object-src 'none'")
    })

    it('restricts connect-src to self', () => {
      const connectSrc = csp.split(';').find(d => d.trim().startsWith('connect-src'))
      expect(connectSrc?.trim()).toBe("connect-src 'self'")
    })

    it('CSP nonce matches x-script-nonce header', () => {
      const scriptNonce = result?.headers.get('x-script-nonce')
      expect(csp).toContain(`'nonce-${scriptNonce}'`)
    })
  })

  describe('CSP header in development mode', () => {
    let csp: string

    beforeEach(() => {
      vi.stubEnv('NODE_ENV', 'development')
      const req = buildRequest('/dashboard', { user: { name: 'Alice' } })
      const result = middlewareCallback(req)
      csp = result?.headers.get('Content-Security-Policy') ?? ''
    })

    it('includes unsafe-inline in script-src', () => {
      const scriptSrc = csp.split(';').find(d => d.trim().startsWith('script-src'))
      expect(scriptSrc).toContain("'unsafe-inline'")
    })

    it('includes localhost in connect-src', () => {
      const connectSrc = csp.split(';').find(d => d.trim().startsWith('connect-src'))
      expect(connectSrc).toContain('http://localhost:*')
      expect(connectSrc).toContain('ws://localhost:*')
    })

    it('does not include upgrade-insecure-requests', () => {
      expect(csp).not.toContain('upgrade-insecure-requests')
    })
  })
})
