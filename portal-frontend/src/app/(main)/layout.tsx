import type { Metadata } from 'next'
import { Geist, Geist_Mono } from 'next/font/google'

import { auth } from '@/auth'
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
  const session = await auth()
  const user = session?.user

  return (
    <html lang="en">
      <body className={`${geistSans.variable} ${geistMono.variable} antialiased`}>
        <div className="[--header-height:calc(--spacing(14))]">
          <Header />
          <SidebarProvider className="h-[calc(100svh-var(--header-height))]  min-h-[calc(100svh-var(--header-height))]">
            <SideBar user={user} />
            <div className="flex flex-1 flex-col gap-4 p-4 w-[calc(100%-var(--sidebar-width))]">{children}</div>
          </SidebarProvider>
        </div>
      </body>
    </html>
  )
}

export default RootLayout
