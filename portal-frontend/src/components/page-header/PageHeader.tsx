'use client'

import { HTMLAttributes, JSX } from 'react'

import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

import { SegmentedControlBar, SegmentedControlBarProps } from '../segmented-control-bar/SegmentedControlBar'
import { Badge } from '../ui/badge'
import { TabSectionProps, TabsSection } from './components/TabsSections'

export type PageHeaderProps<TabValue extends string> = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  subtitle?: string
  segmentedControlBarSectionProps?: SegmentedControlBarProps<TabValue>
  badgeTitle?: string
  customElement?: JSX.Element
  tabsSectionProps?: TabSectionProps
}

export const PageHeader = <TabValue extends string>(props: PageHeaderProps<TabValue>) => {
  const {
    title,
    subtitle,
    className,
    style,
    tabsSectionProps,
    segmentedControlBarSectionProps,
    badgeTitle,
    customElement,
  } = props
  const isMobile = useIsMobile()
  return (
    <div
      id="pageHeader"
      data-testid="pageHeader"
      className={cn(
        'w-full max-w-full  flex flex-col gap-[var(--layout-padding)] h-[var(--title-height)] py-[var(--layout-padding)] border-b-1',
        className,
      )}
      style={style}
    >
      {tabsSectionProps && (
        <TabsSection
          testId="primaryTabs"
          tabs={tabsSectionProps.tabs}
          onClick={tabsSectionProps.onClick}
          selectedTab={tabsSectionProps.selectedTab}
        />
      )}
      <div className={cn('flex')}>
        <div id="pageHeaderTitle" className={cn('flex-1 w-full min-w-0 px-[var(--layout-padding)]')}>
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
              {badgeTitle && (
                <Badge data-testid="pageHeaderBadge" variant="outline">
                  {badgeTitle}
                </Badge>
              )}
            </div>
          )}
          {subtitle && <p className="mt-6 text-muted-foreground">{subtitle}</p>}
        </div>
        <div className="pr-[var(--layout-padding)]">{customElement}</div>
      </div>
      {segmentedControlBarSectionProps && (
        <SegmentedControlBar
          tabs={segmentedControlBarSectionProps.tabs}
          selectedTab={segmentedControlBarSectionProps.selectedTab}
          onTabChange={segmentedControlBarSectionProps.onTabChange}
          completedTabs={segmentedControlBarSectionProps.completedTabs}
          disabledTabs={segmentedControlBarSectionProps.disabledTabs}
          hasCompletionStatus={segmentedControlBarSectionProps.hasCompletionStatus}
          className="mx-[var(--layout-padding)]"
        />
      )}
    </div>
  )
}
