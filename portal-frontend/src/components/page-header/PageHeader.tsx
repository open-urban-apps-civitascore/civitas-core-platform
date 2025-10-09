import { HTMLAttributes, JSX } from 'react'

import { cn } from '@/lib/utils'

import { TabSectionProps, TabsSection } from './components/TabsSections'

export type PageHeaderProps = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  tabs?: TabSectionProps
  customElement?: JSX.Element
  subTabs?: TabSectionProps
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, customElement, className, style, tabs, subTabs } = props

  return (
    <div id="heading" className={cn('flex flex-col h-[var(--title-height)]  border-b-1', className)} style={style}>
      {tabs && (
        <TabsSection tabs={tabs.tabs} onClick={tabs.onClick} selectedTab={tabs.selectedTab} isHeading={!title} />
      )}
      <div id="heading" className={`flex-1 flex ${title ? 'justify-between mt-1' : 'justify-end'} items-center`}>
        {title && (
          <h1
            id="page-heading"
            className="bg-transparent my-1 text-3xl font-bold text-center px-[calc(var(--layout-padding))]"
          >
            {title}
          </h1>
        )}
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
    </div>
  )
}
