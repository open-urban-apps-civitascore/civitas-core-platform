'use client'

import { usePathname } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'
import { usePermissions } from '@/hooks/use-permissions'

import {
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '../../ui/sidebar'
import { appSidebarNavSections, NavItem } from '../appSidebarItems'
import { CollapsibleNavItem } from './CollapsibleNavItem'

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
                <CollapsibleNavItem
                  key={item.title}
                  item={item as NavItem & { items: NavItem[] }}
                  pathname={pathname}
                  tNav={tNav}
                />
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
