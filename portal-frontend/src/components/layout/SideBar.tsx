import { BookOpen, Bot, ChevronRight, ChevronsUpDown, LogOut, SquareTerminal } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import { auth, signOut } from '@/auth'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from '@/components/ui/sidebar'

export const navMain = [
  {
    title: 'home',
    url: '/',
    icon: BookOpen,
  },
  {
    title: 'datasets',
    url: '/datasets',
    icon: BookOpen,
  },
  {
    title: 'menu-item 1',
    url: '#',
    icon: SquareTerminal,
    isActive: true,
    items: [
      {
        title: 'sub-item 1',
        url: '#',
      },
      {
        title: 'sub-item 2',
        url: '#',
      },
      {
        title: 'sub-item 3',
        url: '#',
      },
    ],
  },
  {
    title: 'menu-item 2',
    url: '#',
    icon: Bot,
    items: [
      {
        title: 'sub-item 1',
        url: '#',
      },
      {
        title: 'sub-item 2',
        url: '#',
      },
      {
        title: 'sub-item 3',
        url: '#',
      },
    ],
  },
  {
    title: 'documentation',
    url: '#',
    icon: BookOpen,
  },
]

export const SideBar = async () => {
  const session = await auth()
  const user = session?.user

  const t = await getTranslations('sidebar')

  const getMenuItemTitle = (item: (typeof navMain)[number] | { title: string; url: string }) => {
    const [name, number] = item.title.split(' ')
    const title = t(name)
    return `${title} ${number ?? ''}`
  }

  return (
    <Sidebar className="top-(--header-height) h-[calc(100svh-var(--header-height))]!">
      <SidebarContent>
        <SidebarGroup>
          <SidebarMenu>
            {navMain.map(item => (
              <Collapsible key={item.title} asChild defaultOpen={item.isActive}>
                <SidebarMenuItem>
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
                            <SidebarMenuSubItem key={getMenuItemTitle(subItem)}>
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

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <SidebarMenuButton
                  size="lg"
                  className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
                >
                  <Avatar className="h-8 w-8 rounded-lg">
                    <AvatarImage src={user?.image || ''} alt={user?.name || ''} />
                    <AvatarFallback className="rounded-lg">{user?.name?.charAt(0) || 'G'}</AvatarFallback>
                  </Avatar>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-medium">{user?.name || 'Guest'}</span>
                    <span className="truncate text-xs">{user?.email || ''}</span>
                  </div>
                  <ChevronsUpDown className="ml-auto size-4" />
                </SidebarMenuButton>
              </DropdownMenuTrigger>

              <DropdownMenuContent
                className="w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg"
                align="end"
                sideOffset={4}
              >
                <DropdownMenuLabel className="p-0 font-normal">
                  <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                    <Avatar className="h-8 w-8 rounded-lg">
                      <AvatarImage src={user?.image || ''} alt={user?.name || ''} />
                      <AvatarFallback className="rounded-lg">{user?.name?.charAt(0) || 'G'}</AvatarFallback>
                    </Avatar>
                    <div className="grid flex-1 text-left text-sm leading-tight">
                      <span className="truncate font-medium">{user?.name || 'Guest'}</span>
                      <span className="truncate text-xs">{user?.email || ''}</span>
                    </div>
                  </div>
                </DropdownMenuLabel>

                <DropdownMenuSeparator />

                <form
                  action={async () => {
                    'use server'
                    await signOut({ redirectTo: '/login' })
                  }}
                >
                  <DropdownMenuItem asChild>
                    <button type="submit" className="w-full flex items-center">
                      <LogOut className="mr-2 h-4 w-4" />
                      Log out
                    </button>
                  </DropdownMenuItem>
                </form>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  )
}
