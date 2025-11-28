import { HTMLAttributes } from 'react'

import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

import { Badge } from '../ui/badge'
import { TabSectionProps, TabsSection } from './components/TabsSections'

export type PageHeaderProps = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  subtitle?: string
  tabs?: TabSectionProps
  subTabs?: TabSectionProps
  badgeTitle?: string
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, subtitle, className, style, tabs, subTabs, badgeTitle } = props
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
      <div id="heading" className="flex-1  w-full min-w-0 px-[var(--layout-padding)]">
        {title && (
          <div className="flex flex-row items-center gap-4">
            <h1
              id="page-heading"
              className={cn(
                'block bg-transparent text-3xl font-bold text-center m-0 truncate max-w-full min-w-0',
                isMobile && 'text-2xl',
              )}
            >
              {title}
            </h1>
            {badgeTitle && <Badge variant="outline">{badgeTitle}</Badge>}
          </div>
        )}
        {subtitle && <p className="mt-6 text-muted-foreground">{subtitle}</p>}
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
