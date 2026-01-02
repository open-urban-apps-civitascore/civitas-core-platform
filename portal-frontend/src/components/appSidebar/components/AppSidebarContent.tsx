import { ChevronDown } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import Link from 'next/link'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '../../ui/collapsible'
import {
  SidebarContent,
  SidebarGroup,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '../../ui/sidebar'
import { appSidebarNavItems } from '../appSidebarItems'

export const AppSidebarContent = async () => {
  const tNav = await getTranslations('sidebar')

  const getMenuItemTitle = (item: { title: string; url: string }) => {
    const [name, number] = item.title.split(' ')
    const title = tNav(name)
    return `${title} ${number ?? ''}`
  }

  return (
    <SidebarContent>
      <SidebarContent>
        <SidebarGroup>
          {appSidebarNavItems.map(item =>
            item.items ? (
              <Collapsible key={item.title} defaultOpen={item.isActive} className="group/collapsible">
                <CollapsibleTrigger>
                  <SidebarMenuButton>
                    <item.icon />
                    <span>{getMenuItemTitle(item)}</span>
                    <ChevronDown className="ml-auto transition-transform group-data-[state=open]/collapsible:rotate-180" />
                  </SidebarMenuButton>
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <SidebarMenuSub>
                    {item.items?.map(subItem => (
                      <SidebarMenuSubItem
                        key={getMenuItemTitle(subItem)}
                        data-testid={`sidebarMenuItem-${subItem.title}`}
                      >
                        <SidebarMenuSubButton asChild>
                          <Link href={subItem.url}>
                            <span>{getMenuItemTitle(subItem)}</span>
                          </Link>
                        </SidebarMenuSubButton>
                      </SidebarMenuSubItem>
                    ))}
                  </SidebarMenuSub>
                </CollapsibleContent>
              </Collapsible>
            ) : (
              <SidebarMenuItem key={item.title}>
                <SidebarMenuButton asChild>
                  <Link href={item.url}>
                    <item.icon />
                    <span>{getMenuItemTitle(item)}</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ),
          )}
        </SidebarGroup>
      </SidebarContent>
    </SidebarContent>
  )
}
