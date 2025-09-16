import { DropdownMenu, DropdownMenuContent } from '@radix-ui/react-dropdown-menu'
import { ChevronsUpDown } from 'lucide-react'
import React from 'react'

import { DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem } from '@/components/ui/sidebar'

import { OrganizationInfo } from './OrganizationInfo'

interface AppSidebarHeaderProps {
  currentOrganization: { organizationName: string; tenant: string }
  organizations: { organizationName: string; tenant: string }[]
}

export const AppSidebarHeader = (props: AppSidebarHeaderProps) => {
  const { currentOrganization, organizations } = props

  return (
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <SidebarMenuButton
                size="lg"
                className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
              >
                <OrganizationInfo
                  organizationName={currentOrganization?.organizationName}
                  tenant={currentOrganization?.tenant}
                />
                <ChevronsUpDown className="ml-auto size-4" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>

            <DropdownMenuContent
              className="w-(--radix-dropdown-menu-trigger-width) min-w-56 bg-white z-10 rounded-lg"
              align="end"
              sideOffset={4}
            >
              {organizations.map(({ organizationName, tenant }) => (
                <React.Fragment key={organizationName}>
                  <DropdownMenuItem asChild>
                    <OrganizationInfo organizationName={organizationName} tenant={tenant} />
                  </DropdownMenuItem>

                  <DropdownMenuSeparator />
                </React.Fragment>
              ))}
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
  )
}
