import { Separator } from '../ui/separator'
import { SidebarTrigger } from '../ui/sidebar'
import { BreadcrumbNavigation } from './components/breadcrumb-navigation/BreadcrumbNavigation'
import { LanguageSelect } from './components/language-select/LanguageSelect'

export const AppHeader = async () => {
  return (
    <header className="sticky top-0 left-400 bg-transparent w-full">
      <div className="flex mt-3.5">
        <div className="flex flex-1 items-center px-4">
          <div className="flex items-center gap-2 px-4">
            <SidebarTrigger className="-ml-1" />

            <Separator orientation="vertical" className="mr-2 data-[orientation=vertical]:h-4" />

            <BreadcrumbNavigation />
          </div>
          <div className="flex items-center ml-auto gap-4 pr-4">
            <LanguageSelect />
          </div>
        </div>
      </div>
    </header>
  )
}
