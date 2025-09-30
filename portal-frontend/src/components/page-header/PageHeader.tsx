import React, { HTMLAttributes, JSX } from 'react'

import { cn } from '@/lib/utils'

import { TabHeaderProps, TabsSection } from './components/TabsSections'
import { TitleHeaderProps, TitleSection } from './components/TitleSection'

export interface BasePageHeaderProps extends Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> {
  customElement?: JSX.Element
  isTabHeader?: boolean
}

type PageHeaderProps = BasePageHeaderProps & (TabHeaderProps | TitleHeaderProps)

export const PageHeader = (props: PageHeaderProps) => {
  const { customElement, className, style, isTabHeader = false } = props

  if (isTabHeader && 'tabs' in props && 'onClick' in props && 'selectedTab' in props) {
    return (
      <div id="heading" className={cn('flex justify-between h-[var(--title-height)]', className)} style={style}>
        <TabsSection
          tabs={props.tabs}
          onClick={props.onClick}
          selectedTab={props.selectedTab}
          isTabHeader={isTabHeader}
        />
        {customElement}
      </div>
    )
  }

  if ('title' in props) {
    return (
      <div id="heading" className={cn('flex justify-between h-[var(--title-height)]', className)} style={style}>
        <TitleSection title={props.title} subtitle={props?.subtitle} isTabHeader={false} />
        {customElement}
      </div>
    )
  }

  return null
}
