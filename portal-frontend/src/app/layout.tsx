import './globals.css'

import type { Metadata } from 'next'
import { IBM_Plex_Mono, IBM_Plex_Sans } from 'next/font/google'
import { SessionProvider } from 'next-auth/react'
import { NextIntlClientProvider } from 'next-intl'
import { getLocale } from 'next-intl/server'

import { SessionManager } from '@/components/session-manager'

import QueryProvider from './providers/queryClient'

const ibmPlexSans = IBM_Plex_Sans({
  variable: '--font-ibm-plex-sans',
  subsets: ['latin'],
  weight: ['300', '400', '500', '600', '700'],
})

const ibmPlexMono = IBM_Plex_Mono({
  variable: '--font-ibm-plex-mono',
  subsets: ['latin'],
  weight: ['300', '400', '500', '600', '700'],
})

export const metadata: Metadata = {
  title: 'CIVITAS/CORE V2',
  description: 'The frontend vor CIVITAS/CORE V2',
  icons: {
    icon: [
      { url: '/favicon-light.ico' },
      {
        url: '/favicon-light.ico',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/favicon-dark.ico',
        media: '(prefers-color-scheme: dark)',
      },
    ],
  },
}

interface RootLayoutProps {
  children: React.ReactNode
}

const RootLayout = async ({ children }: RootLayoutProps) => {
  const locale = await getLocale()

  return (
    <html lang="en">
      <body className={`${ibmPlexSans.variable}  ${ibmPlexMono.variable} antialiased`}>
        <SessionProvider>
          <SessionManager />
          <NextIntlClientProvider locale={locale}>
            <QueryProvider>{children}</QueryProvider>
          </NextIntlClientProvider>
        </SessionProvider>
      </body>
    </html>
  )
}

export default RootLayout
