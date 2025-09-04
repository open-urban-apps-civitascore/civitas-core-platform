/* eslint-disable @typescript-eslint/naming-convention */
/**
 * Authentication utility functions for token management
 */

interface TokenRefreshResponse {
  access_token: string
  expires_in: number
  refresh_token?: string
}

interface RefreshTokenResult {
  access_token: string
  expires_at: number
  refresh_token: string
  error?: 'RefreshTokenError'
}

/**
 * Refreshes the access token using the refresh token with Keycloak
 * @param refreshToken - The refresh token to use for getting a new access token
 * @returns Promise with new token data or error
 */
export const refreshAccessToken = async (refreshToken: string): Promise<RefreshTokenResult> => {
  try {
    // Keycloak token endpoint
    const keycloakIssuer = process.env.KEYCLOAK_ISSUER!
    const tokenEndpoint = `${keycloakIssuer}/protocol/openid-connect/token`

    const response = await fetch(tokenEndpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        client_id: process.env.KEYCLOAK_CLIENT_ID!,
        client_secret: process.env.KEYCLOAK_CLIENT_SECRET!,
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
      }),
    })

    const responseJson = await response.json()

    if (!response.ok) {
      console.error('Token refresh failed:', response.status, response.statusText, responseJson)
      throw responseJson
    }

    const newTokens = responseJson as TokenRefreshResponse

    return {
      access_token: newTokens.access_token,
      expires_at: Math.floor(Date.now() / 1000 + newTokens.expires_in),
      // Use old refresh token in case no new one was issued (some providers only issue refresh tokens once)
      refresh_token: newTokens.refresh_token ?? refreshToken,
    }
  } catch (error) {
    console.error('Error refreshing access_token', error)
    return {
      access_token: '',
      expires_at: 0,
      refresh_token: refreshToken,
      error: 'RefreshTokenError',
    }
  }
}

/**
 * Checks if a token is expired or will expire soon
 * @param expiresAt - Token expiration timestamp in seconds
 * @param bufferSeconds - Number of seconds before expiry to consider as "expired" (default: 0)
 * @returns boolean indicating if token is expired or will expire within buffer time
 */
export const isTokenExpired = (expiresAt: number): boolean => {
  if (!expiresAt) return true
  const expiryWithBuffer = (expiresAt - 60) * 1000
  const isExpired = Date.now() >= expiryWithBuffer
  return isExpired
}
