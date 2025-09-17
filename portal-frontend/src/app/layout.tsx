import './globals.css'

import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { SessionProvider } from 'next-auth/react'
import { NextIntlClientProvider } from 'next-intl'
import { getLocale } from 'next-intl/server'

import { SessionManager } from '@/components/session-manager'

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
})

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
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
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
        <SessionProvider refetchInterval={240}>
          <SessionManager />
          <NextIntlClientProvider locale={locale}>{children}</NextIntlClientProvider>
        </SessionProvider>
      </body>
    </html>
  )
}

export default RootLayout
