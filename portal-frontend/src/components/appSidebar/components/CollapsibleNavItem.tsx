'use client'

import { ChevronRight } from 'lucide-react'
import { useEffect, useState } from 'react'

import { GuardedLink } from '@/components/guarded-link/GuardedLink'

import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '../../ui/collapsible'
import {
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '../../ui/sidebar'
import { NavItem } from '../appSidebarItems'

interface CollapsibleNavItemProps {
  item: NavItem & { items: NavItem[] }
  pathname: string
  tNav: (key: string) => string
}

export const CollapsibleNavItem = (props: CollapsibleNavItemProps) => {
  const { item, pathname, tNav } = props
  const isActiveGroup = item.items.some(subItem => pathname.startsWith(subItem.url))
  const [open, setOpen] = useState(isActiveGroup)

  useEffect(() => {
    if (isActiveGroup) {
      setOpen(true)
    }
  }, [isActiveGroup])

  return (
    <Collapsible asChild open={open} onOpenChange={setOpen} className="group/collapsible">
      <SidebarMenuItem>
        <CollapsibleTrigger asChild>
          <SidebarMenuButton tooltip={tNav(item.title)} className="cursor-pointer" isActive={isActiveGroup}>
            {item.icon && <item.icon />}
            <span>{tNav(item.title)}</span>
            <ChevronRight className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90" />
          </SidebarMenuButton>
        </CollapsibleTrigger>
        <CollapsibleContent>
          <SidebarMenuSub>
            {item.items.map(subItem => (
              <SidebarMenuSubItem key={subItem.title} data-testid={`sidebarMenuItem-${subItem.title}`}>
                <SidebarMenuSubButton asChild isActive={pathname.startsWith(subItem.url)}>
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
  )
}
