import { Search } from 'lucide-react'
import { getTranslations } from 'next-intl/server'

import { LanguageSelect } from '@/components/language-select/LanguageSelect'
import { Command } from '@/components/ui/command'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { SidebarInput } from '@/components/ui/sidebar'

export const Header = async () => {
  const t = await getTranslations('common')

  return (
    <header className="bg-background sticky top-0 z-50 flex w-full items-center border-b">
      <div className="flex h-(--header-height) w-full items-center gap-2 px-4">
        <a href="#">
          <div className="bg-sidebar-primary text-sidebar-primary-foreground flex aspect-square size-8 items-center justify-center rounded-lg rounded-lg">
            <Command className="size-4" />
          </div>
        </a>
        <Separator orientation="vertical" className="mr-2 h-4" />
        <form className="w-full sm:ml-auto sm:w-auto">
          <div className="relative">
            <Label htmlFor="search" className="sr-only">
              Search
            </Label>
            <SidebarInput id="search" placeholder={`${t('type-to-search')}...`} className="h-8 pl-7" />
            <Search className="pointer-events-none absolute top-1/2 left-2 size-4 -translate-y-1/2 opacity-50 select-none" />
          </div>
        </form>
        <LanguageSelect />
      </div>
    </header>
  )
}
