'use client'

import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { cn } from '@/lib/utils'

export type Tab<TabValue> = { value: TabValue; label: string; isActive?: boolean }

interface SegmentedControlBarProps<TabValue extends string> {
  tabs: Tab<TabValue>[]
  selectedTab: TabValue
  onTabChange: (tab: TabValue) => void
  completedTabs?: TabValue[]
  disabledTabs?: TabValue[]
}

export const SegmentedControlBar = <TabValue extends string>(props: SegmentedControlBarProps<TabValue>) => {
  const { tabs, selectedTab, onTabChange, completedTabs = [], disabledTabs = [] } = props
  const t = useTranslations()

  const getTabLabel = (tabLabel: string): string => {
    return t(tabLabel)
  }

  return (
    <div className="flex flex-wrap gap-1 p-1 bg-accent rounded-lg" data-testid="segmentedControlBar">
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
            className={cn(
              'flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors',
              isSelected ? 'bg-background shadow-sm' : 'bg-transparent hover:bg-background/50',
              isDisabled && 'opacity-50 cursor-not-allowed',
            )}
          >
            {isCompleted ? (
              <CircleCheckBig className="w-4 h-4 text-green-600" />
            ) : (
              <CircleDashed className="w-4 h-4 text-muted-foreground" />
            )}
            <span>{getTabLabel(tab.label)}</span>
          </button>
        )
      })}
    </div>
  )
}
