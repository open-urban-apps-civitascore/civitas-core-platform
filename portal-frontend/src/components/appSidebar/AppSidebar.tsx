import { CurrentUser } from '@/types/currentUser'

import { Sidebar } from '../ui/sidebar'
import { AppSidebarContent } from './components/AppSidebarContent'
import { AppSidebarFooter } from './components/AppSidebarFooter'
import { AppSidebarHeader } from './components/AppSidebarHeader'

interface AppSidebarProps {
  currentUser: CurrentUser
}

export const AppSidebar = async (props: AppSidebarProps) => {
  const { currentUser } = props

  return (
    <nav aria-label="Main navigation">
      <Sidebar collapsible="icon">
        <AppSidebarHeader />

        <AppSidebarContent />

        <AppSidebarFooter currentUser={currentUser} />
      </Sidebar>
    </nav>
  )
}
