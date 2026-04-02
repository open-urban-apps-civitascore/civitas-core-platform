import Image from 'next/image'

import { SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem } from '@/components/ui/sidebar'

export const AppSidebarHeader = () => {
  const tenantName = process.env.NEXT_PUBLIC_TENANT_NAME ?? 'Mandanten-Name'

  return (
    <SidebarHeader>
      <SidebarMenu>
        <SidebarMenuItem>
          <SidebarMenuButton size="lg" className="pointer-events-none">
            <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-[#036aa1]">
              <Image
                src="/images/only_logo_civitas.svg"
                alt="CIVITAS/CORE Logo"
                width={20}
                height={20}
                className="brightness-0 invert"
              />
            </div>
            <div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
              <span className="truncate font-semibold">{tenantName}</span>
              <span className="truncate text-xs text-muted-foreground">CIVITAS/CORE</span>
            </div>
          </SidebarMenuButton>
        </SidebarMenuItem>
      </SidebarMenu>
    </SidebarHeader>
  )
}
