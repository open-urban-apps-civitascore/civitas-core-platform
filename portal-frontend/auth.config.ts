import type { NextAuthConfig } from 'next-auth'

export const authConfig = {
  session: {
    strategy: 'jwt',
    maxAge: 30 * 24 * 60 * 60, // 30 days
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
    jwt({ token, account, profile }) {
      if (account && profile) {
        token.accessToken = account.access_token
        token.refreshToken = account.refresh_token
        token.expiresAt = account.expires_at
      }
      return token
    },
    session({ session, token }) {
      session.accessToken = token.accessToken as string
      session.error = token.error as string
      return session
    },
  },
  providers: [], // Providers are handled in auth.ts
} satisfies NextAuthConfig
