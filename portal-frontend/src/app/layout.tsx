import './globals.css'

import type { Metadata } from 'next'
import { IBM_Plex_Mono, IBM_Plex_Sans } from 'next/font/google'
import { SessionProvider } from 'next-auth/react'
import { NextIntlClientProvider } from 'next-intl'
import { getLocale } from 'next-intl/server'

import { SessionManager } from '@/components/session-manager'

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
  title: 'CIVITAS/Core v2',
  description: 'The frontend vor CIVITAS/Core v2',
}

interface RootLayoutProps {
  children: React.ReactNode
}

const RootLayout = async ({ children }: RootLayoutProps) => {
  const locale = await getLocale()

  return (
    <html lang="en">
      <body className={`${ibmPlexSans.variable}  ${ibmPlexMono.variable} antialiased`}>
        <SessionProvider refetchInterval={240}>
          <SessionManager />
          <NextIntlClientProvider locale={locale}>{children}</NextIntlClientProvider>
        </SessionProvider>
      </body>
    </html>
  )
}

export default RootLayout
