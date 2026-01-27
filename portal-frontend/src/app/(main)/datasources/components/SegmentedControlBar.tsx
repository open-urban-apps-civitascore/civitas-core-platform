'use client'

import { CircleCheckBig, CircleDashed } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { cn } from '@/lib/utils'

export type DatasourceTab = 'basicInfo' | 'connector' | 'dataStructure' | 'accessPermissions' | 'dataspaces'

interface SegmentedControlBarProps {
  selectedTab: DatasourceTab
  onTabChange: (tab: DatasourceTab) => void
  completedTabs?: DatasourceTab[]
  disabledTabs?: DatasourceTab[]
}

const tabs: DatasourceTab[] = ['basicInfo', 'connector', 'dataStructure', 'accessPermissions', 'dataspaces']

export const SegmentedControlBar = (props: SegmentedControlBarProps) => {
  const { selectedTab, onTabChange, completedTabs = [], disabledTabs = [] } = props
  const t = useTranslations('datasources.tabs')

  const getTabLabel = (tab: DatasourceTab): string => {
    return t(tab)
  }

  return (
    <div className="flex flex-wrap gap-1 p-1 bg-accent rounded-lg" data-testid="segmentedControlBar">
      {tabs.map(tab => {
        const isSelected = selectedTab === tab
        const isCompleted = completedTabs.includes(tab)
        const isDisabled = disabledTabs.includes(tab)

        return (
          <button
            key={tab}
            data-testid={`tab-${tab}`}
            type="button"
            onClick={() => !isDisabled && onTabChange(tab)}
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
            <span>{getTabLabel(tab)}</span>
          </button>
        )
      })}
    </div>
  )
}
