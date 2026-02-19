import { NextRequest, NextResponse } from 'next/server'
import { getToken } from 'next-auth/jwt'

import { GetRequestLogContext, logger } from '@/lib/logger'

const JSON_SERVER_URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`
const API_URL = `${process.env.API_BASE_URL}:${process.env.API_PORT}/v2`

// Log configured endpoints on module initialization
logger.debug({ jsonServerUrl: JSON_SERVER_URL, apiUrl: API_URL }, 'Proxy endpoints configured')
interface RouteContext {
  params: Promise<{ path: string[] }>
}

/**
 * Generic proxy route that forwards all requests to backend and attaches access token.
 */

const proxyRequest = async (request: NextRequest, context: RouteContext, method: string) => {
  // Get safe request context for logging (no sensitive data)
  const RequestContext = GetRequestLogContext(request)

  logger.debug(RequestContext, 'Proxy request received')

  // Determine if we should use secure cookies based on the forwarded protocol
  const forwardedProto = request.headers.get('x-forwarded-proto')
  const isSecure = forwardedProto === 'https'

  const token = await getToken({
    req: request,
    secret: process.env.NEXTAUTH_SECRET,
    secureCookie: isSecure,
  })

  // Check authentication status (no sensitive token data logged)
  if (!token?.access_token) {
    logger.debug({ method, url: request.url }, 'Unauthorized: No access token')
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  if (token.error === 'RefreshTokenError') {
    logger.debug({ method, url: request.url }, 'Unauthorized: Session expired')
    return NextResponse.json({ error: 'Session expired' }, { status: 401 })
  }

  try {
    const { path } = await context.params
    const pathString = path.join('/')
    const searchParams = request.nextUrl.searchParams.toString()
    const searchParamsString = searchParams ? `?${searchParams}` : ''

    // Forward all original headers from the request
    const headers = new Headers(request.headers)

    const isApiRequest = headers.get('x-api-request') === 'true'
    const baseUrl = isApiRequest ? API_URL : JSON_SERVER_URL
    const url = `${baseUrl}/${pathString}${searchParamsString}`

    logger.debug({ method, path: pathString, isApiRequest }, 'Routing request')

    // Attach authorization token
    headers.set('Authorization', `Bearer ${token.access_token}`)

    // Remove host header to avoid conflicts with backend
    headers.delete('host')

    const fetchOptions: RequestInit = { method, headers }

    // Include body for methods that support it
    if (['POST', 'PUT', 'PATCH'].includes(method) && url.includes(`${API_URL}`)) {
      fetchOptions.body = await request.arrayBuffer()
    }

    // Include body for methods that support it for json-server
    // This can be removed once json-server is not used anymore
    if (['POST', 'PUT', 'PATCH'].includes(method) && url.includes(`${JSON_SERVER_URL}`)) {
      const req = await request.json()

      const body = req.body ?? req
      fetchOptions.body = JSON.stringify(body)
    }

    const response = await fetch(url, fetchOptions)

    logger.debug({ method, path: pathString, status: response.status }, 'Proxy request completed')

    // Forward response headers from backend
    const responseHeaders = new Headers(response.headers)

    // Non-JSON responses, stream the body directly
    const contentType = response.headers.get('Content-Type')
    if (!contentType || !contentType.includes('application/json')) {
      return new NextResponse(response.body, {
        status: response.status,
        headers: responseHeaders,
      })
    }

    // Remove encoding headers for JSON responses
    responseHeaders.delete('content-encoding')
    responseHeaders.delete('content-length')
    responseHeaders.delete('transfer-encoding')

    // JSON responses
    const data = await response.json()
    return NextResponse.json(data, {
      status: response.status,
      headers: responseHeaders,
    })
  } catch (error) {
    logger.error(
      {
        method,
        url: request.url,
        error: error instanceof Error ? error.message : String(error),
        stack: error instanceof Error ? error.stack : undefined,
      },
      'Proxy request failed',
    )
    return NextResponse.json({ error: 'Proxy error' }, { status: 500 })
  }
}

export const GET = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'GET')

export const POST = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'POST')

export const PUT = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'PUT')

export const PATCH = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'PATCH')

export const DELETE = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'DELETE')
