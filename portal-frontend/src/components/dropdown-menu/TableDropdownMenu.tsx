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

interface TableDropdownMenuProps {
  menuItems: DropDownMenuItem[]
  menuContentClassName?: string
}
export const TableDropdownMenu = (props: TableDropdownMenuProps) => {
  const { menuItems, menuContentClassName } = props

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button className="h-9 w-9" variant="outline" aria-label="Open menu" size="sm">
          <MoreVerticalIcon />
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
