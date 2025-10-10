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
    <div
      id="heading"
      className={cn(
        'flex flex-col gap-[var(--layout-padding)] h-[var(--title-height)] py-[var(--layout-padding)] border-b-1',
        className,
      )}
      style={style}
    >
      {tabs && (
        <TabsSection tabs={tabs.tabs} onClick={tabs.onClick} selectedTab={tabs.selectedTab} isHeading={!title} />
      )}
      <div
        id="heading"
        className={`flex-1 flex ${title ? 'justify-between' : 'justify-end'} items-center px-[var(--layout-padding)]`}
      >
        {title && (
          <h1 id="page-heading" className=" bg-transparent text-3xl font-bold text-center m-0">
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
          className={`bg-accent`}
        />
      )}
    </div>
  )
}
