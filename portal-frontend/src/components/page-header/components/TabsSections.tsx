'use client'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type Tab = { value: string; label: string; isActive?: boolean }

export interface TabSectionProps {
  tabs: Tab[]
  onClick: (tab: string) => void
  selectedTab: string
  className?: string
  testId?: string
}

export const TabsSection = (props: TabSectionProps) => {
  const { tabs, onClick, selectedTab, className, testId } = props

  return (
    <div
      data-testid={testId}
      role="tablist"
      className={cn('px-[calc(var(--layout-padding))] max-w-full self-start border-b-1 w-full')}
    >
      <div
        className={cn(
          'max-w-full w-auto flex justify-start gap-2 self-start items-end flex-nowrap overflow-x-auto',
          className,
        )}
      >
        {tabs.map(tab => (
          <Button
            variant="ghost"
            key={tab.value}
            onClick={() => selectedTab !== tab.value && onClick(tab.value)}
            className={cn(
              'h-full text-center text-sm font-medium disabled:pointer-events-auto disabled:cursor-not-allowed',
              `p-3 pt-0 border-b-2 border-transparent rounded-none hover:bg-white hover:border-primary underline-offset-10 decoration-1 ${selectedTab === tab.value ? ' border-primary' : 'text-slate-400'}`,
            )}
            style={{ margin: 0 }}
            id={`heading-${tab.value}`}
            role="tab"
            aria-selected={selectedTab === tab.value}
            tabIndex={0}
            disabled={tab.isActive === false}
          >
            {tab.label}
          </Button>
        ))}
      </div>
    </div>
  )
}
