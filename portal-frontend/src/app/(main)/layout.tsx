import { dehydrate, HydrationBoundary, QueryClient } from '@tanstack/react-query'
import { redirect } from 'next/navigation'

import { AppHeader } from '@/components/appHeader/AppHeader'
import { AppSidebar } from '@/components/appSidebar/AppSidebar'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'
import { Toaster } from '@/components/ui/sonner'
import { AuthError } from '@/lib/serverFetch'
import { CurrentUser } from '@/types/currentUser'

import { getCurrentUser } from '../services/api/users/serverRequests'

interface MainLayoutProps {
  children: React.ReactNode
}

const MainLayout = async (props: MainLayoutProps) => {
  let currentUser: CurrentUser
  try {
    currentUser = await getCurrentUser()
  } catch (error) {
    redirect(error instanceof AuthError ? '/api/auth/signout' : '/error')
  }

  const queryClient = new QueryClient()
  queryClient.setQueryData(['currentUser'], currentUser)

  const { children } = props

  return (
    <SidebarProvider>
      <HydrationBoundary state={dehydrate(queryClient)}>
        <AppSidebar currentUser={currentUser} />
        <SidebarInset className="h-svh  w-[calc(100%-var(--sidebar-width))] [--header-height:calc(--spacing(13))] [--layout-padding:calc(--spacing(6))] overflow-hidden">
          <AppHeader />
          <div className="h-[calc(100%-var(--header-height))] [--title-height:calc(--spacing(30))] [--page-padding:calc(--spacing(4))]">
            {children}
          </div>
          <Toaster
            toastOptions={{
              classNames: {
                toast: 'toast-default',
                error: 'toast-error',
              },
            }}
          />
        </SidebarInset>
      </HydrationBoundary>
    </SidebarProvider>
  )
}

export default MainLayout
