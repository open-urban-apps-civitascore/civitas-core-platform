import { HTMLAttributes, JSX } from 'react'

import { cn } from '@/lib/utils'

import { TabSectionProps, TabsSection } from './components/TabsSections'
import { TitleSection, TitleSectionProps } from './components/TitleSection'

export type PageHeaderProps = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  subtitle?: string
  tabs?: TabSectionProps
  customElement?: JSX.Element
  subTabs?: TabSectionProps
}

export const PageHeader = (props: PageHeaderProps) => {
  const { title, subtitle, customElement, className, style, tabs, subTabs } = props

  return (
    <div id="heading" className={cn('flex justify-between items-center h-[var(--title-height)]', className)} style={style}>
      <div className='flex flex-col justify-center'>
        {tabs && <TabsSection tabs={tabs.tabs} onClick={tabs.onClick} selectedTab={tabs.selectedTab} />}
        <div id="heading" className="flex justify-between">
          {title && <TitleSection title={title} subtitle={subtitle} />}
        </div>
        {subTabs && (
          <TabsSection
            tabs={subTabs.tabs}
            selectedTab={subTabs.selectedTab}
            onClick={subTabs.onClick}
            className='mt-4 bg-accent'
          />
        )}
      </div>
      {customElement}
    </div>
  )
}
