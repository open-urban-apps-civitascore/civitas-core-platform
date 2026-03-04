import { Separator } from '../ui/separator'
import { SidebarTrigger } from '../ui/sidebar'
import { BreadcrumbNavigation } from './components/breadcrumb-navigation/BreadcrumbNavigation'
import { LanguageSelect } from './components/language-select/LanguageSelect'

export const AppHeader = () => {
  return (
    <header className="sticky top-0 border-b-1 left-400 bg-transparent w-full h-[calc(var(--header-height))]">
      <div className="flex h-full items-center px-4">
        <div className="flex items-center gap-2 pr-4">
          <SidebarTrigger className="-ml-1 size-8" variant="outline" />
          <Separator orientation="vertical" className="data-[orientation=vertical]:h-4" />
          <BreadcrumbNavigation />
        </div>
        <div className="flex items-center ml-auto gap-4 pr-4">
          <LanguageSelect />
        </div>
      </div>
    </header>
  )
}
