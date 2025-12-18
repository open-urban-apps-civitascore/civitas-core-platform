import { ChevronRight } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '../../ui/collapsible'
import {
  SidebarContent,
  SidebarGroup,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '../../ui/sidebar'
import { appSidebarNavItems } from '../appSidebarMockItems'

export const AppSidebarContent = async () => {
  const tNav = await getTranslations('sidebar')

  const getMenuItemTitle = (item: { title: string; url: string }) => {
    const [name, number] = item.title.split(' ')
    const title = tNav(name)
    return `${title} ${number ?? ''}`
  }

  return (
    <SidebarContent>
      <SidebarGroup>
        <SidebarMenu>
          {appSidebarNavItems.map(item => (
            <Collapsible key={item.title} asChild defaultOpen={item.isActive}>
              <SidebarMenuItem data-testid={`sidebarMenuItem-${item.title}`}>
                <SidebarMenuButton asChild tooltip={getMenuItemTitle(item)}>
                  <a href={item.url}>
                    <item.icon />
                    <span>{getMenuItemTitle(item)}</span>
                  </a>
                </SidebarMenuButton>

                {item.items?.length ? (
                  <>
                    <CollapsibleTrigger asChild>
                      <SidebarMenuAction className="data-[state=open]:rotate-90">
                        <ChevronRight />
                        <span className="sr-only">Toggle</span>
                      </SidebarMenuAction>
                    </CollapsibleTrigger>

                    <CollapsibleContent>
                      <SidebarMenuSub>
                        {item.items?.map(subItem => (
                          <SidebarMenuSubItem
                            key={getMenuItemTitle(subItem)}
                            data-testid={`sidebarMenuItem-${subItem.title}`}
                          >
                            <SidebarMenuSubButton asChild>
                              <a href={subItem.url}>
                                <span>{getMenuItemTitle(subItem)}</span>
                              </a>
                            </SidebarMenuSubButton>
                          </SidebarMenuSubItem>
                        ))}
                      </SidebarMenuSub>
                    </CollapsibleContent>
                  </>
                ) : null}
              </SidebarMenuItem>
            </Collapsible>
          ))}
        </SidebarMenu>
      </SidebarGroup>
    </SidebarContent>
  )
}
