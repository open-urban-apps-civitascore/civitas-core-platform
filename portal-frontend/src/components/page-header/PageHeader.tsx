import { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

import { Badge } from '../ui/badge'
import { TabSectionProps, TabsSection } from './components/TabsSections'

export type PageHeaderProps = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  tabs?: TabSectionProps
  subTabs?: TabSectionProps
  badgeTitle?: string
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, className, style, tabs, subTabs, badgeTitle } = props
  return (
    <div
      id="pageHeader"
      className={cn(
        'flex flex-col gap-[var(--layout-padding)] h-[var(--title-height)] py-[var(--layout-padding)] border-b-1',
        className,
      )}
      style={style}
    >
      {tabs && (
        <TabsSection
          testId="primaryTabs"
          tabs={tabs.tabs}
          onClick={tabs.onClick}
          selectedTab={tabs.selectedTab}
          isHeading={!title}
        />
      )}
      <div
        id="pageHeaderTitle"
        className={`flex-1 flex ${title ? 'justify-between' : 'justify-end'} items-center px-[var(--layout-padding)]`}
      >
        {title && (
          <div className="flex flex-row items-center gap-4">
            <h1 id="page-heading" className=" bg-transparent text-3xl font-bold text-center m-0">
              {title}
            </h1>
            {badgeTitle && (
              <Badge data-testid="pageHeaderBadge" variant="outline">
                {badgeTitle}
              </Badge>
            )}
          </div>
        )}
      </div>
      {subTabs && (
        <TabsSection
          testId="subTabs"
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
