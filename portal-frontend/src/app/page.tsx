import { LanguageSelect } from "@/components/language-select/LanguageSelect";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Command } from "@/components/ui/command";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";

import { Separator } from "@/components/ui/separator";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarInput,
  SidebarInset,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarProvider,
} from "@/components/ui/sidebar";

import {
  BookOpen,
  Bot,
  ChevronRight,
  ChevronsUpDown,
  LogOut,
  Search,
  SquareTerminal,
} from 'lucide-react'
import { auth, signOut } from '../../auth'
import { useTranslations } from "next-intl";
import { getTranslations } from "next-intl/server";

const navMain = [
    {
      title: "menu-item 1",
      url: "#",
      icon: SquareTerminal,
      isActive: true,
      items: [
        {
          title: "sub-item 1",
          url: "#",
        },
        {
          title: "sub-item 2",
          url: "#",
        },
        {
          title: "sub-item 3",
          url: "#",
        },
      ],
    },
    {
      title: "menu-item 2",
      url: "#",
      icon: Bot,
      items: [
        {
          title: "sub-item 1",
          url: "#",
        },
        {
          title: "sub-item 2",
          url: "#",
        },
        {
          title: "sub-item 3",
          url: "#",
        },
      ],
    },
    {
      title: "documentation",
      url: "#",
      icon: BookOpen,
    },
  ]

export default async function Page() {
  const session = await auth()
  const user = session?.user
  const t = await getTranslations("common");
  const tNav = await getTranslations("sidebar");

  const getMenuItemTitle = (
    item: (typeof navMain)[number] | { title: string; url: string }
  ) => {
    const [name, number] = item.title.split(" ");
    const title = tNav(name);
    return `${title} ${number ?? ""}`;
  };

  return (
    <div className="[--header-height:calc(--spacing(14))]">
      <SidebarProvider className="flex flex-col">
        <header className="bg-background sticky top-0 z-50 flex w-full items-center border-b">
          <div className="flex h-(--header-height) w-full items-center gap-2 px-4">
            <a href="#">
              <div className="bg-sidebar-primary text-sidebar-primary-foreground flex aspect-square size-8 items-center justify-center rounded-lg">
                <Command className="size-4" />
              </div>
            </a>

            <Separator orientation="vertical" className="mr-2 h-4" />

            <form className="w-full sm:ml-auto sm:w-auto">
              <div className="relative">
                <Label htmlFor="search" className="sr-only">
                  Search
                </Label>

                <SidebarInput
                  id="search"
                  placeholder={`${t("type-to-search")}...`}
                  className="h-8 pl-7"
                />
                <Search className="pointer-events-none absolute top-1/2 left-2 size-4 -translate-y-1/2 opacity-50 select-none" />
              </div>
            </form>
            <LanguageSelect />
          </div>
        </header>

        <div className="flex flex-1">
          <Sidebar className="top-(--header-height) h-[calc(100svh-var(--header-height))]!">
            <SidebarContent>
              <SidebarGroup>
                <SidebarMenu>
                  {navMain.map(item => (
                    <Collapsible
                      key={item.title}
                      asChild
                      defaultOpen={item.isActive}
                    >
                      <SidebarMenuItem>
                        <SidebarMenuButton
                          asChild
                          tooltip={getMenuItemTitle(item)}
                        >
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
                                {item.items?.map((subItem) => (
                                  <SidebarMenuSubItem
                                    key={getMenuItemTitle(subItem)}
                                  >
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
                          <AvatarImage
                            src={user?.image || ''}
                            alt={user?.name || ''}
                          />
                          <AvatarFallback className="rounded-lg">
                            {user?.name?.charAt(0) || 'G'}
                          </AvatarFallback>
                        </Avatar>
                        <div className="grid flex-1 text-left text-sm leading-tight">
                          <span className="truncate font-medium">
                            {user?.name || 'Guest'}
                          </span>
                          <span className="truncate text-xs">
                            {user?.email || ''}
                          </span>
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
                            <AvatarImage 
                              src={user?.image || ''} 
                              alt={user?.name || ''} 
                            />
                            <AvatarFallback className="rounded-lg">
                              {user?.name?.charAt(0) || 'G'}
                            </AvatarFallback>
                          </Avatar>
                          <div className="grid flex-1 text-left text-sm leading-tight">
                            <span className="truncate font-medium">
                              {user?.name || 'Guest'}
                            </span>
                            <span className="truncate text-xs">
                              {user?.email || ''}
                            </span>
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

          <SidebarInset>
            <div className="flex flex-1 flex-col gap-4 p-4">
              <div className="grid auto-rows-min gap-4 md:grid-cols-3">
                <div className="bg-muted/50 aspect-video rounded-xl" />
                <div className="bg-muted/50 aspect-video rounded-xl" />
                <div className="bg-muted/50 aspect-video rounded-xl" />
              </div>
              <div className="bg-muted/50 min-h-[100vh] flex-1 rounded-xl md:min-h-min" />
            </div>
          </SidebarInset>
        </div>
      </SidebarProvider>
    </div>
  );
}
