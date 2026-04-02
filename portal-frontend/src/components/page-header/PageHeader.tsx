'use client'

import { HTMLAttributes, JSX, useRef } from 'react'

import { useIsTruncated } from '@/hooks/use-is-truncated'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

import { SegmentedControlBar, SegmentedControlBarProps } from '../segmented-control-bar/SegmentedControlBar'
import { Badge } from '../ui/badge'
import { Tooltip, TooltipContent, TooltipTrigger } from '../ui/tooltip'
import { TabSectionProps, TabsSection } from './components/TabsSections'

export type PageHeaderProps<TabValue extends string> = Pick<HTMLAttributes<HTMLDivElement>, 'className' | 'style'> & {
  title?: string
  subtitle?: string
  segmentedControlBarProps?: SegmentedControlBarProps<TabValue>
  badgeTitle?: string
  customElement?: JSX.Element
  tabsSectionProps?: TabSectionProps
}

export const PageHeader = <TabValue extends string>(props: PageHeaderProps<TabValue>) => {
  const { title, subtitle, className, style, tabsSectionProps, segmentedControlBarProps, badgeTitle, customElement } =
    props
  const isMobile = useIsMobile()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const isTruncated = useIsTruncated(titleRef)
  return (
    <div
      id="pageHeader"
      data-testid="pageHeader"
      className={cn(
        'w-full max-w-full flex flex-col gap-[var(--layout-padding)] h-[var(--title-height)] py-[var(--layout-padding)] border-b-1 overflow-hidden',
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
      <div className={cn('flex min-w-0')}>
        <div id="pageHeaderTitle" className={cn('flex-1 w-full min-w-0 px-[var(--layout-padding)]')}>
          {title && (
            <div className="flex flex-row items-center gap-4">
              <Tooltip open={isTruncated ? undefined : false}>
                <TooltipTrigger asChild>
                  <h1
                    ref={titleRef}
                    id="page-heading"
                    tabIndex={0}
                    className={cn(
                      'block bg-transparent text-3xl font-bold text-center m-0 truncate max-w-full min-w-0',
                      isMobile && 'text-2xl',
                    )}
                  >
                    {title}
                  </h1>
                </TooltipTrigger>
                <TooltipContent variant="secondary">{title}</TooltipContent>
              </Tooltip>
              {badgeTitle && (
                <Badge data-testid="pageHeaderBadge" variant="outline">
                  {badgeTitle}
                </Badge>
              )}
            </div>
          )}
          {subtitle && <p className="mt-6 text-muted-foreground whitespace-pre-line">{subtitle}</p>}
        </div>
        <div className="pr-[var(--layout-padding)]">{customElement}</div>
      </div>
      {segmentedControlBarProps && (
        <SegmentedControlBar
          tabs={segmentedControlBarProps.tabs}
          selectedTab={segmentedControlBarProps.selectedTab}
          onTabChange={segmentedControlBarProps.onTabChange}
          completedTabs={segmentedControlBarProps.completedTabs}
          disabledTabs={segmentedControlBarProps.disabledTabs}
          disabledTabTooltips={segmentedControlBarProps.disabledTabTooltips}
          tabsWithNoCompletionStatus={segmentedControlBarProps.tabsWithNoCompletionStatus}
          hasCompletionStatus={segmentedControlBarProps.hasCompletionStatus}
          className="mx-[var(--layout-padding)]"
        />
      )}
    </div>
  )
}
