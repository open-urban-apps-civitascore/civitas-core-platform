/* eslint-disable @typescript-eslint/naming-convention */
import { JWT } from '@auth/core/jwt'
import type { NextAuthConfig } from 'next-auth'

import { isTokenExpired, refreshAccessToken } from './src/lib/token-utils'

export const authConfig = {
  session: {
    strategy: 'jwt',
    maxAge: 10 * 60 * 60, // 10 hours
  },
  pages: {
    signIn: '/login',
  },
  events: {
    async signOut(message) {
      // Handle Keycloak logout when NextAuth signOut is called
      const keycloakIssuer = process.env.KEYCLOAK_ISSUER

      if (!keycloakIssuer) {
        console.error('KEYCLOAK_ISSUER environment variable is not set')
        return
      }
      const { token } = message as { token: JWT | null }

      try {
        const logoutParams = new URLSearchParams({
          client_id: process.env.KEYCLOAK_CLIENT_ID || '',
        })

        // Adding id_token_hint if available for seamless logout
        if (token?.id_token) {
          logoutParams.set('id_token_hint', token.id_token as string)
        }

        const keycloakLogoutUrl = `${keycloakIssuer}/protocol/openid-connect/logout?${logoutParams.toString()}`

        await fetch(keycloakLogoutUrl, {
          method: 'GET',
        })

        console.log('Keycloak logout completed')
      } catch (error) {
        console.error('Error during Keycloak logout:', error)
      }
    },
  },
  callbacks: {
    authorized({ auth, request: { nextUrl } }) {
      const isLoggedIn = !!auth?.user
      const isOnLogin = nextUrl.pathname === '/login'
      const isPublicPath = isOnLogin || nextUrl.pathname.startsWith('/api/auth')

      // Allow public paths
      if (isPublicPath) {
        return true
      }

      return isLoggedIn
    },

    async jwt({ token, account }) {
      if (account) {
        // First-time login, save the access_token, its expiry, refresh_token, and id_token
        return {
          ...token,
          access_token: account.access_token,
          expires_at: account.expires_at,
          refresh_token: account.refresh_token,
          id_token: account.id_token,
          refresh_attempts: 0,
        }
      } else if (!isTokenExpired(token.expires_at as number)) {
        // Access_token is still valid, reset retry counter
        return { ...token, refresh_attempts: 0 }
      } else {
        // Access_token has expired, try to refresh it
        if (!token.refresh_token) {
          console.error('Missing refresh_token')
          return { ...token, error: 'RefreshTokenError' }
        }

        const attempts = (token.refresh_attempts as number) || 0
        if (attempts >= 3) {
          console.error('Maximum refresh attempts reached')
          return null
        }

        const refreshResult = await refreshAccessToken(token.refresh_token as string)

        if (refreshResult.error) {
          return { ...token, refresh_attempts: attempts + 1, error: 'RefreshTokenError' }
        }

        return {
          ...token,
          access_token: refreshResult.access_token,
          expires_at: refreshResult.expires_at,
          refresh_token: refreshResult.refresh_token,
          refresh_attempts: 0,
        }
      }
    },

    session({ session, token }) {
      return {
        ...session,
        error: token.error as string | undefined,
      }
    },
  },
  providers: [], // Providers are handled in auth.ts
} satisfies NextAuthConfig
