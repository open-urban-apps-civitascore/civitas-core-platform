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
      <SidebarInset className="h-svh  w-[calc(100%-var(--sidebar-width))] [--header-height:calc(--spacing(13))] [--layout-padding:calc(--spacing(6))]">
        <AppHeader />
        <div className="h-[calc(100%-var(--header-height))] [--title-height:calc(--spacing(30))] [--page-padding:calc(--spacing(4))]">
          {children}
        </div>
      </SidebarInset>
    </SidebarProvider>
  )
}

export default MainLayout
