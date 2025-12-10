import { NextRequest, NextResponse } from 'next/server'
import { getToken } from 'next-auth/jwt'

const BACKEND_URL = 'http://localhost:3001'

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
    const url = `${BACKEND_URL}/${pathString}${searchParams ? `?${searchParams}` : ''}`

    const headers: HeadersInit = {
      Authorization: `Bearer ${token.access_token}`,
      /* eslint-disable @typescript-eslint/naming-convention */
      'Content-Type': 'application/json',
    }

    const fetchOptions: RequestInit = { method, headers }

    // Include body for methods that support it
    if (['POST', 'PUT', 'PATCH'].includes(method)) {
      const body = await request.text()
      if (body) {
        fetchOptions.body = body
      }
    }

    const response = await fetch(url, fetchOptions)

    const contentType = response.headers.get('content-type')
    if (!contentType || !contentType.includes('application/json')) {
      return new NextResponse(null, { status: response.status })
    }

    const data = await response.json()
    return NextResponse.json(data, { status: response.status })
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
