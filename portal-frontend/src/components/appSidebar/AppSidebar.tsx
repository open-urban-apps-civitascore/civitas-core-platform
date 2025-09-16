import { Sidebar } from '../ui/sidebar'
import { currentOrganization, expampleOrganizations } from './appSidebarMockItems'
import { AppSidebarContent } from './components/AppSidebarContent'
import { AppSidebarFooter } from './components/AppSidebarFooter'
import { AppSidebarHeader } from './components/AppSidebarHeader'

interface AppSidebarProps {
  user?: {
    name?: string | null
    email?: string | null
    image?: string | null
  } | null
}

export const AppSidebar = async (props: AppSidebarProps) => {
  const { user } = props

  return (
    <nav aria-label="Main navigation">
      <Sidebar collapsible="icon">
        <AppSidebarHeader currentOrganization={currentOrganization} organizations={expampleOrganizations} />

        <AppSidebarContent />

        <AppSidebarFooter user={user} />
      </Sidebar>
    </nav>
  )
}
