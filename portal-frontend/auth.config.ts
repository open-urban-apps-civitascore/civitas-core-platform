import type { NextAuthConfig } from 'next-auth'
import { refreshAccessToken, isTokenExpired } from './src/lib/token-utils'

export const authConfig = {
  session: {
    strategy: 'jwt',
    maxAge: 10 * 60 * 60, // 10 hours
  },
  pages: {
    signIn: '/login',
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
        // First-time login, save the `access_token`, its expiry and the `refresh_token`
        return {
          ...token,
          access_token: account.access_token,
          expires_at: account.expires_at,
          refresh_token: account.refresh_token,
        }
      } else if (!isTokenExpired(token.expires_at as number)) {
        // Access_token is still valid
        return token
      } else {
        // Access_token has expired, try to refresh it
        if (!token.refresh_token) {
          console.error("Missing refresh_token")
          return { ...token, error: "RefreshTokenError" }
        }

        const refreshResult = await refreshAccessToken(token.refresh_token as string)
        
        if (refreshResult.error) {
          return null
        }

        return {
          ...token,
          access_token: refreshResult.access_token,
          expires_at: refreshResult.expires_at,
          refresh_token: refreshResult.refresh_token,
        }
      }
    },
    session({ session, token }) {
      session.accessToken = token.access_token as string
      session.error = token.error as string | undefined
      return session
    },
  },
  providers: [], // Providers are handled in auth.ts
} satisfies NextAuthConfig
