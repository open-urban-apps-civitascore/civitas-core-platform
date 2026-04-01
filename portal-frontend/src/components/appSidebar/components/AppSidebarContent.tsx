'use client'

import { ChevronRight } from 'lucide-react'
import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { usePermissions } from '@/hooks/use-permissions'

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
import { appSidebarNavSections, NavItem } from '../appSidebarItems'

export const AppSidebarContent = () => {
  const tNav = useTranslations('sidebar')
  const pathname = usePathname()
  const { hasPermission } = usePermissions()

  const isVisible = (item: NavItem) => !item.requiredPermission || hasPermission(item.requiredPermission)

  const visibleSections = appSidebarNavSections
    .map(section => ({
      ...section,
      items: section.items
        .map(item => ({
          ...item,
          items: item.items?.filter(isVisible),
        }))
        .filter(item => {
          if (item.items) return item.items.length > 0
          return isVisible(item)
        }),
    }))
    .filter(section => section.items.length > 0)

  return (
    <SidebarContent>
      {visibleSections.map(section => (
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
                      <SidebarMenuButton
                        tooltip={tNav(item.title)}
                        className="cursor-pointer"
                        isActive={item.items.some(subItem => pathname === subItem.url)}
                      >
                        {item.icon && <item.icon />}
                        <span>{tNav(item.title)}</span>
                        <ChevronRight className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
                      </SidebarMenuButton>
                    </CollapsibleTrigger>
                    <CollapsibleContent>
                      <SidebarMenuSub>
                        {item.items.map(subItem => (
                          <SidebarMenuSubItem key={subItem.title} data-testid={`sidebarMenuItem-${subItem.title}`}>
                            <SidebarMenuSubButton asChild isActive={pathname === subItem.url}>
                              <GuardedLink href={subItem.url}>
                                <span>{tNav(subItem.title)}</span>
                              </GuardedLink>
                            </SidebarMenuSubButton>
                          </SidebarMenuSubItem>
                        ))}
                      </SidebarMenuSub>
                    </CollapsibleContent>
                  </SidebarMenuItem>
                </Collapsible>
              ) : (
                <SidebarMenuItem key={item.title}>
                  <SidebarMenuButton asChild tooltip={tNav(item.title)} isActive={pathname === item.url}>
                    {item.external ? (
                      <a href={item.url} target="_blank" rel="noopener noreferrer">
                        {item.icon && <item.icon />}
                        <span>{tNav(item.title)}</span>
                      </a>
                    ) : (
                      <GuardedLink href={item.url}>
                        {item.icon && <item.icon />}
                        <span>{tNav(item.title)}</span>
                      </GuardedLink>
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
