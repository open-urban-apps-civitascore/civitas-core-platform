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
        <Button className="h-9 w-9" variant="outline" aria-label="Open menu" size="sm">
          <MoreVerticalIcon />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-40" align="end">
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
