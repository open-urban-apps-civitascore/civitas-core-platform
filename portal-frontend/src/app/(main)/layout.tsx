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
      <SidebarInset>
        <AppHeader />
        <div className="p-4">{children}</div>
      </SidebarInset>
    </SidebarProvider>
  )
}

export default MainLayout
