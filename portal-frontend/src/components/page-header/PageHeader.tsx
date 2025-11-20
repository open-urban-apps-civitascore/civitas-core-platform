import { HTMLAttributes } from 'react'

import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

import { TabSectionProps, TabsSection } from './components/TabsSections'

export type PageHeaderProps = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  tabs?: TabSectionProps
  subTabs?: TabSectionProps
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, className, style, tabs, subTabs } = props
  const isMobile = useIsMobile()

  return (
    <div
      id="heading"
      className={cn(
        'w-full max-w-full  flex flex-col gap-[var(--layout-padding)] h-[var(--title-height)] py-[var(--layout-padding)] border-b-1',
        className,
      )}
      style={style}
    >
      {tabs && (
        <TabsSection tabs={tabs.tabs} onClick={tabs.onClick} selectedTab={tabs.selectedTab} isHeading={!title} />
      )}
      <div
        id="heading"
        className={`flex-1 flex w-full min-w-0 ${title ? 'justify-between' : 'justify-end'} items-center px-[var(--layout-padding)]`}
      >
        {title && (
          <h1
            id="page-heading"
            className={cn(
              'block bg-transparent text-3xl font-bold text-center m-0 truncate max-w-full min-w-0',
              isMobile && 'text-2xl',
            )}
          >
            {title}
          </h1>
        )}
      </div>
      {subTabs && (
        <TabsSection
          tabs={subTabs.tabs}
          selectedTab={subTabs.selectedTab}
          onClick={subTabs.onClick}
          isSubTabsSection={!!subTabs}
          className="bg-accent"
        />
      )}
    </div>
  )
}
