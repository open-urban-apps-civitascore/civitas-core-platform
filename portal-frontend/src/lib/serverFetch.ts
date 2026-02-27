import { headers as nextHeaders } from 'next/headers'
import { getToken } from 'next-auth/jwt'

/**
 * Server-side fetch utility for direct backend communication
 * Reads the access token directly from the encrypted JWT cookie via getToken().
 */

interface ServerFetchConfig {
  endpoint: string
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  params?: URLSearchParams
  body?: unknown
  headers?: Record<string, string>
  /**
   * If true, request goes to the real API backend
   * If false/undefined, uses JSON Server (for development/testing)
   */
  isApiBackend?: boolean
}

interface ApiResponse<T> {
  content: T
  totalElements?: number
  totalPages?: number
  last?: boolean
  first?: boolean
  numberOfElements?: number
  size?: number
  number?: number
  empty?: boolean
}

export interface ServerFetchResponse<T> {
  data: T
  totalElements?: number
  totalPages?: number
}

/**
 * Fetch data directly from the backend using server-side authentication
 * @param config - Fetch configuration
 * @returns Response data with metadata
 * @throws Error if not authenticated or fetch fails
 */
export const serverFetch = async <TResponse>({
  endpoint,
  method = 'GET',
  params,
  body,
  headers = {},
  isApiBackend = false,
}: ServerFetchConfig): Promise<ServerFetchResponse<TResponse>> => {
  // Read the JWT token directly from the encrypted cookie
  const requestHeaders = await nextHeaders()
  const forwardedProto = requestHeaders.get('x-forwarded-proto')
  const isSecure = forwardedProto === 'https'

  const token = await getToken({
    req: { headers: requestHeaders },
    secret: process.env.NEXTAUTH_SECRET,
    secureCookie: isSecure,
  })

  if (!token) {
    throw new Error('Not authenticated')
  }

  const accessToken = token.access_token as string | undefined

  if (!accessToken) {
    throw new Error('No access token available')
  }

  // Determine which backend to use (similar to API route logic)
  const JSON_SERVER_HOST = process.env.JSON_SERVER_HOST || 'http://localhost'
  const JSON_SERVER_PORT = process.env.JSON_SERVER_PORT || '3001'
  const API_BASE_URL = process.env.API_BASE_URL
  const API_PORT = process.env.API_PORT

  const JSON_SERVER_URL = `${JSON_SERVER_HOST}:${JSON_SERVER_PORT}`
  const API_URL = API_BASE_URL && API_PORT ? `${API_BASE_URL}:${API_PORT}/v2` : undefined

  // Choose base URL based on isApiBackend flag
  const baseUrl = isApiBackend ? API_URL : JSON_SERVER_URL

  if (!baseUrl) {
    throw new Error('Backend URL not configured')
  }

  const searchParams = params ? `?${params.toString()}` : ''
  const url = `${baseUrl}${endpoint}${searchParams}`

  // Prepare fetch options
  const fetchOptions: RequestInit = {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,

      ...headers,
    },
    cache: 'no-store', // Equivalent to Cache-Control: no-store
  }

  // Add body for POST/PUT/PATCH requests
  if (body && ['POST', 'PUT', 'PATCH'].includes(method)) {
    fetchOptions.body = JSON.stringify(body)
  }

  try {
    const response = await fetch(url, fetchOptions)

    if (!response.ok) {
      throw new Error(`Backend responded with status ${response.status}`)
    }

    const res = await response.json()

    const data: ApiResponse<TResponse> = res.content
      ? res
      : {
          content: res,
          totalElements: Array.isArray(res) ? Number(response.headers.get('x-total-count')) || 0 : undefined,
        }
    return {
      data: data.content,
      totalElements: data.totalElements,
      totalPages: data.totalPages,
    }
  } catch (error) {
    console.error(`Server fetch failed for ${endpoint}:`, error)
    throw error
  }
}
