import { NextRequest, NextResponse } from 'next/server'
import { getToken } from 'next-auth/jwt'

const JSON_SERVER_URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`
const API_URL = `${process.env.API_BASE_URL}:${process.env.API_PORT}/v2`
interface RouteContext {
  params: Promise<{ path: string[] }>
}

/**
 * Generic proxy route that forwards all requests to backend and attaches access token.
 */

const proxyRequest = async (request: NextRequest, context: RouteContext, method: string) => {
  const token = await getToken({ req: request, secret: process.env.NEXTAUTH_SECRET })
  if (!token?.access_token) {
    return NextResponse.json({ error: 'Unauthorized' }, { status: 401 })
  }

  if (token.error === 'RefreshTokenError') {
    return NextResponse.json({ error: 'Session expired' }, { status: 401 })
  }

  try {
    const { path } = await context.params
    const pathString = path.join('/')
    const searchParams = request.nextUrl.searchParams.toString()

    const searchParamsString = searchParams ? `?${searchParams}` : ''
    const useApiUrl = pathString === 'users' || pathString.startsWith('models')
    const url = `${useApiUrl ? API_URL : JSON_SERVER_URL}/${pathString}${searchParamsString}`

    // Forward all original headers from the request
    const headers = new Headers(request.headers)

    headers.set('Authorization', `Bearer ${token.access_token}`)

    // Remove host header to avoid conflicts with backend
    headers.delete('host')

    const fetchOptions: RequestInit = { method, headers }

    // Include body for methods that support it
    if (['POST', 'PUT', 'PATCH'].includes(method)) {
      fetchOptions.body = await request.arrayBuffer()
    }

    const response = await fetch(url, fetchOptions)

    // Forward response headers from backend
    const responseHeaders = new Headers(response.headers)

    // Non-JSON responses, stream the body directly
    const contentType = response.headers.get('content-type')
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
    console.error('Proxy error:', error)
    return NextResponse.json({ error: 'Proxy error' }, { status: 500 })
  }
}

export const GET = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'GET')

export const POST = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'POST')

export const PUT = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'PUT')

export const PATCH = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'PATCH')

export const DELETE = (request: NextRequest, context: RouteContext) => proxyRequest(request, context, 'DELETE')
