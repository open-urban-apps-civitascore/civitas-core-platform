import './globals.css'

import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'
import { NextIntlClientProvider } from 'next-intl'
import { getLocale } from 'next-intl/server'

import { Header } from '@/components/layout/Header'
import { SideBar } from '@/components/layout/SideBar'
import { SidebarProvider } from '@/components/ui/sidebar'

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
        <div className="[--header-height:calc(--spacing(14))]">
          <NextIntlClientProvider locale={locale}>
            <Header />
            <SidebarProvider className="h-[calc(100svh-var(--header-height))]  min-h-[calc(100svh-var(--header-height))]">
              <SideBar />
              <div className="flex flex-1 flex-col gap-4 p-4">{children}</div>
            </SidebarProvider>
          </NextIntlClientProvider>
        </div>
      </body>
    </html>
  )
}

export default RootLayout
