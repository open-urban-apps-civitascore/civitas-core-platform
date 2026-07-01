import { NextResponse } from 'next/server'

import { auth } from '../auth'

// Generate secure nonce for CSP using Web Crypto API (Edge Runtime compatible)
const generateNonce = () => {
  const array = new Uint8Array(16)
  crypto.getRandomValues(array)
  return btoa(String.fromCharCode(...array))
}

export default auth(req => {
  const { nextUrl } = req
  if (nextUrl.pathname !== '/login' && !req.auth?.user) {
    return NextResponse.redirect(new URL('/login', req.url))
  }
  const response = NextResponse.next()

  // Generate unique nonces for each request
  const scriptNonce = generateNonce()
  const styleNonce = generateNonce()

  const isProduction = process.env.NODE_ENV === 'production'

  // Development CSP - more permissive for dev tools while maintaining security
  const developmentCSP = [
    // Fallback policy: only allow resources from same origin by default
    "default-src 'self'",

    // Scripts: Allow same-origin + nonce-authorized + unsafe-inline fallback
    // - 'self': Next.js bundles and same-origin scripts
    // - nonce: Pre-authorized inline scripts (dynamic per request)
    // - unsafe-inline: Fallback for dev tools, hot reload, React DevTools
    `script-src 'self' 'nonce-${scriptNonce}' 'unsafe-inline'`,

    // Styles: Allow same-origin + all inline styles (required for Tailwind CSS v4 + React)
    // - 'self': CSS files from same origin
    // - unsafe-inline: Tailwind generates dynamic inline styles, React components use inline styles
    // Note: Cannot use nonces here as they would disable unsafe-inline completely
    "style-src 'self' 'unsafe-inline'",

    // Images: Allow same-origin + data URIs
    // - 'self': Images served from same origin
    // - data:: Base64 encoded images, SVG data URIs, often used by icons and dev tools
    "img-src 'self' data:",

    // Fonts: Allow same-origin + data URIs
    // - 'self': Web fonts served from same origin
    // - data:: Base64 encoded fonts, used by icon libraries and embedded fonts
    "font-src 'self' data:",

    // Network connections: Allow same-origin + localhost (dev servers, APIs, WebSockets)
    // - 'self': API calls to same origin
    // - localhost:*: Local dev servers, hot reload, API mocking, database connections
    // - ws://localhost:*: WebSocket connections for hot reload and dev tools
    "connect-src 'self' http://localhost:* ws://localhost:* https://localhost:*",

    // Objects: Block all plugin content (Flash, Java applets, etc.) - prevents legacy attack vectors
    "object-src 'none'",

    // Base URI: Only allow same-origin base tags - prevents base tag hijacking attacks
    "base-uri 'self'",

    // Form actions: Only allow same-origin form submissions - prevents CSRF and form hijacking
    "form-action 'self'",

    // Frame ancestors: Don't allow this site to be embedded - prevents clickjacking attacks
    "frame-ancestors 'none'",
  ]

  // Production CSP - maximum security while maintaining functionality
  const productionCSP = [
    // Fallback policy: only allow resources from same origin by default
    "default-src 'self'",

    // Scripts: Strict nonce-only policy for maximum security
    // - 'self': Next.js bundles and same-origin scripts
    // - nonce: ONLY pre-authorized inline scripts with unique per-request nonce
    // - No unsafe-inline: Eliminates major XSS attack vector in production
    `script-src 'self' 'nonce-${scriptNonce}'`,

    // Styles: Allow same-origin + inline styles (technical requirement)
    // - 'self': CSS files from same origin
    // - unsafe-inline: Required for Tailwind CSS v4 + React component inline styles
    // Note: Modern React frameworks generate dynamic inline styles that cannot be pre-authorized with nonces
    "style-src 'self' 'unsafe-inline'",

    // Images: Strict same-origin only (no data URIs in production)
    // - 'self': Only images served from same origin
    // - Blocks data URIs to prevent potential data exfiltration and reduce attack surface
    "img-src 'self'",

    // Fonts: Strict same-origin only (no data URIs in production)
    // - 'self': Only web fonts served from same origin
    // - Blocks data URIs to reduce attack surface
    "font-src 'self'",

    // Network connections: Strict same-origin (add production API domains here)
    // - 'self': API calls to same origin only
    // - TODO: add production API domains
    "connect-src 'self'",

    // Objects: Block all plugin content - prevents legacy attack vectors (Flash, Java, etc.)
    "object-src 'none'",

    // Base URI: Only allow same-origin base tags - prevents base tag hijacking attacks
    "base-uri 'self'",

    // Form actions: Only allow same-origin form submissions - prevents CSRF and form hijacking
    "form-action 'self'",

    // Frame ancestors: Don't allow this site to be embedded - prevents clickjacking attacks
    "frame-ancestors 'none'",

    // Force HTTPS: Automatically upgrade HTTP requests to HTTPS in production
    'upgrade-insecure-requests',
  ]

  // Set CSP header with dynamic nonces
  response.headers.set('Content-Security-Policy', (isProduction ? productionCSP : developmentCSP).join('; '))

  // Set other security headers. Cache-Control: no-store is provided by Next.js for dynamic routes
  // (see next.config.ts), so it is not set here.
  response.headers.set('X-Content-Type-Options', 'nosniff')
  response.headers.set('X-Frame-Options', 'DENY')
  response.headers.set('Referrer-Policy', 'strict-origin-when-cross-origin')

  // Make nonces available to the application
  response.headers.set('x-script-nonce', scriptNonce)
  response.headers.set('x-style-nonce', styleNonce)

  return response
})

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico|images|.*\\.).*)'],
}
