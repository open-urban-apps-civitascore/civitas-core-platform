'use client'

import { MoreVerticalIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'

type DropDownMenuItem = {
  label: string
  onClick?: () => void
}

interface BasicDropdownMenuProps {
  title?: string
  menuItems: DropDownMenuItem[]
  menuTriggerClassName?: string
  menuContentClassName?: string
}
export const BasicDropdownMenu = (props: BasicDropdownMenuProps) => {
  const { title, menuItems, menuTriggerClassName, menuContentClassName } = props

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button className={menuTriggerClassName} variant="outline" aria-label="Open menu" size="sm">
          {title || <MoreVerticalIcon />}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className={cn('w-40', menuContentClassName)} align="end">
        <DropdownMenuGroup>
          {menuItems.map(item => (
            <DropdownMenuItem key={item.label} onSelect={item.onClick} className="hover:cursor-pointer">
              {item.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
