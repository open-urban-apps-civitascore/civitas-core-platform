import { HTMLAttributes, JSX } from 'react'

import { cn } from '@/lib/utils'

import { TabSectionProps, TabsSection } from './components/TabsSections'
import { TitleSection } from './components/TitleSection'

export type PageHeaderProps = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  subtitle?: string
  tabs?: TabSectionProps
  customElement?: JSX.Element
  subTabs?: TabSectionProps
  shouldShowDivider?: boolean
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, subtitle, customElement, className, style, tabs, subTabs, shouldShowDivider } = props

  return (
    <div id="heading" className={cn('flex flex-col h-[var(--title-height)]', className)} style={style}>
      {tabs && (
        <TabsSection
          tabs={tabs.tabs}
          onClick={tabs.onClick}
          selectedTab={tabs.selectedTab}
          isHeading={!title}
          className="p-0"
        />
      )}
      <div id="heading" className={`flex ${title ? 'justify-between mt-1' : 'justify-end'}`}>
        {title && <TitleSection title={title} subtitle={subtitle} />}
        {customElement}
      </div>
      {subTabs && (
        <TabsSection
          tabs={subTabs.tabs}
          selectedTab={subTabs.selectedTab}
          onClick={subTabs.onClick}
          isSubTabsSection={!!subTabs}
          className={`${title && 'mt-4'} bg-accent`}
        />
      )}
      {shouldShowDivider && <hr className="mt-auto" />}
    </div>
  )
}
