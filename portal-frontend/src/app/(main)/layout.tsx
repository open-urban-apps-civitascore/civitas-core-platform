import { auth } from '@/auth'
import { AppHeader } from '@/components/appHeader/AppHeader'
import { AppSidebar } from '@/components/appSidebar/AppSidebar'
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar'

interface MainLayoutProps {
  children: React.ReactNode
}

const MainLayout = async (props: MainLayoutProps) => {
  const session = await auth()
  const user = session?.user

  const { children } = props

  return (
    <SidebarProvider>
      <AppSidebar user={user} />
      <SidebarInset className="h-svh  w-[calc(100%-var(--sidebar-width))] [--header-height:calc(--spacing(13))] [--layout-padding:calc(--spacing(8))]">
        <AppHeader />
        <div className="flex flex-col gap-4 p-[calc(var(--layout-padding))] h-[calc(100%-var(--header-height))]">
          {children}
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

export default MainLayout
