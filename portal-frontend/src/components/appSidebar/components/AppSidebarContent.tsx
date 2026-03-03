import { ChevronRight } from 'lucide-react'
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '../../ui/collapsible'
import {
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '../../ui/sidebar'
import { appSidebarNavSections } from '../appSidebarItems'

export const AppSidebarContent = async () => {
  const tNav = await getTranslations('sidebar')

  return (
    <SidebarContent>
      {appSidebarNavSections.map(section => (
        <SidebarGroup key={section.title}>
          <SidebarGroupLabel className="group-data-[collapsible=icon]:hidden text-base-sidebar-foreground text-sm font-medium font-['IBM_Plex_Sans'] leading-5 line-clamp-1">
            {tNav(section.title)}
          </SidebarGroupLabel>
          <SidebarMenu>
            {section.items.map(item =>
              item.items ? (
                <Collapsible key={item.title} asChild defaultOpen={item.isActive} className="group/collapsible">
                  <SidebarMenuItem>
                    <CollapsibleTrigger asChild>
                      <SidebarMenuButton tooltip={tNav(item.title)} className="cursor-pointer">
                        {item.icon && <item.icon />}
                        <span>{tNav(item.title)}</span>
                        <ChevronRight className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
                      </SidebarMenuButton>
                    </CollapsibleTrigger>
                    <CollapsibleContent>
                      <SidebarMenuSub>
                        {item.items.map(subItem => (
                          <SidebarMenuSubItem key={subItem.title} data-testid={`sidebarMenuItem-${subItem.title}`}>
                            <SidebarMenuSubButton asChild>
                              <Link href={subItem.url}>
                                <span>{tNav(subItem.title)}</span>
                              </Link>
                            </SidebarMenuSubButton>
                          </SidebarMenuSubItem>
                        ))}
                      </SidebarMenuSub>
                    </CollapsibleContent>
                  </SidebarMenuItem>
                </Collapsible>
              ) : (
                <SidebarMenuItem key={item.title}>
                  <SidebarMenuButton asChild tooltip={tNav(item.title)}>
                    {item.external ? (
                      <a href={item.url} target="_blank" rel="noopener noreferrer">
                        {item.icon && <item.icon />}
                        <span>{tNav(item.title)}</span>
                      </a>
                    ) : (
                      <Link href={item.url}>
                        {item.icon && <item.icon />}
                        <span>{tNav(item.title)}</span>
                      </Link>
                    )}
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ),
            )}
          </SidebarMenu>
        </SidebarGroup>
      ))}
    </SidebarContent>
  )
}
