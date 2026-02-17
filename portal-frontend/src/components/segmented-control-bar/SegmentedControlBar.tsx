'use client'

import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { cn } from '@/lib/utils'

export type Tab<TabValue> = { value: TabValue; label: string }

export interface SegmentedControlBarProps<TabValue extends string> {
  tabs: Tab<TabValue>[]
  selectedTab: TabValue
  onTabChange: (tab: TabValue) => void
  completedTabs?: TabValue[]
  disabledTabs?: TabValue[]
  hasCompletionStatus?: boolean
  className?: string
  testId?: string
}

export const SegmentedControlBar = <TabValue extends string>(props: SegmentedControlBarProps<TabValue>) => {
  const {
    tabs,
    selectedTab,
    onTabChange,
    completedTabs = [],
    disabledTabs = [],
    hasCompletionStatus = false,
    className,
    testId,
  } = props
  const t = useTranslations()

  const getTabLabel = (tabLabel: string): string => {
    return t(tabLabel)
  }

  const getCompletionStatusIcon = (isCompleted: boolean) => {
    if (!hasCompletionStatus) return undefined

    return isCompleted ? (
      <CircleCheckBig className="w-4 h-4 text-green-600" />
    ) : (
      <CircleDashed className="w-4 h-4 text-muted-foreground" />
    )
  }

  return (
    <div
      className={cn('flex flex-wrap gap-1 p-1 bg-accent rounded-lg w-fit', className)}
      data-testid={testId || 'segmentedControlBar'}
      role="tablist"
    >
      {tabs.map(tab => {
        const isSelected = selectedTab === tab.value
        const isCompleted = completedTabs.includes(tab.value)
        const isDisabled = disabledTabs.includes(tab.value)

        return (
          <button
            key={tab.value}
            data-testid={`tab-${tab.value}`}
            type="button"
            onClick={() => !isDisabled && onTabChange(tab.value)}
            disabled={isDisabled}
            role="tab"
            className={cn(
              'flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors h-7.5',
              isSelected ? 'bg-background shadow-sm' : 'bg-transparent hover:bg-background/50',
              isDisabled && 'opacity-50 cursor-not-allowed',
            )}
          >
            {getCompletionStatusIcon(isCompleted)}
            <span>{getTabLabel(tab.label)}</span>
          </button>
        )
      })}
    </div>
  )
}
