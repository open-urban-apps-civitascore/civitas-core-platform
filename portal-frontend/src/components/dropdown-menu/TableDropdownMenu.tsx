'use client'

import { LucideIcon, MoreVerticalIcon } from 'lucide-react'

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
  icon?: LucideIcon
}

interface TableDropdownMenuProps {
  menuItems: DropDownMenuItem[]
  classNameDropdownContent?: string
}
export const TableDropdownMenu = (props: TableDropdownMenuProps) => {
  const { menuItems, classNameDropdownContent } = props
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button className="h-9 w-9" variant="outline" aria-label="Open menu" size="sm">
          <MoreVerticalIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className={cn('w-40', classNameDropdownContent)} align="end">
        <DropdownMenuGroup>
          {menuItems.map((item, index) => {
            const Icon = item.icon
            const isLastItem = index === menuItems.length - 1
            return (
              <DropdownMenuItem
                key={item.label}
                onSelect={item.onClick}
                className={`hover:cursor-pointer ${isLastItem ? '' : 'border-b border-gray-200 rounded-none'}`}
              >
                {Icon && <Icon className="mr-2 h-4 w-4" />}
                {item.label}
              </DropdownMenuItem>
            )
          })}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
