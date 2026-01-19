'use client'

import { MoreHorizontalIcon, MoreVerticalIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

type DropDownMenuItem = {
  label: string
  onClick?: () => void
}

interface TableDropdownMenuProps {
  menuItems: DropDownMenuItem[]
}
export const TableDropdownMenu = (props: TableDropdownMenuProps) => {
  const { menuItems } = props

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" aria-label="Open menu" size="sm">
          <MoreVerticalIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-40" align="end">
        <DropdownMenuGroup>
          {menuItems.map(item => (
            <DropdownMenuItem key={item.label} onSelect={item.onClick}>
              {item.label}
            </DropdownMenuItem>
          ))}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
