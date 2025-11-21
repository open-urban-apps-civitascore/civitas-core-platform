'use client'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type Tab = { value: string; label: string; isActive?: boolean }

export interface TabSectionProps {
  tabs: Tab[]
  onClick: (tab: string) => void
  selectedTab: string
  isSubTabsSection?: boolean
  className?: string
  isHeading?: boolean
  testId?: string
}

export const TabsSection = (props: TabSectionProps) => {
  const { tabs, onClick, selectedTab, isSubTabsSection = false, isHeading = false, className, testId } = props

  const tabsStyles = (tab: Tab) =>
    `p-3 pt-0 border-b-2 border-transparent rounded-none hover:bg-white hover:border-foreground underline-offset-10 decoration-1 ${selectedTab === tab.value ? ' border-foreground' : 'text-slate-400'}`
  const subTabsStyles = (tab: Tab) =>
    `rounded-sm no-underline px-2 py-1 h-7 hover:bg-white ${selectedTab === tab.value && 'bg-white shadow-sm'}`
  const Tag = isHeading ? 'h1' : 'div'

  return (
    <div
      data-testid={testId}
      className={cn(
        `px-[calc(var(--layout-padding))] max-w-full self-start ${!isSubTabsSection && '-mb-1 -mt-2 border-b-1 w-full'}`,
      )}
    >
      <Tag
        className={cn(
          ` max-w-full w-auto flex justify-start gap-2 self-start items-end flex-nowrap overflow-x-auto ${isSubTabsSection && 'rounded-md p-1'}  `,
          className,
        )}
      >
        {tabs.map(tab => (
          <Button
            variant="ghost"
            key={tab.value}
            onClick={() => onClick(tab.value)}
            className={cn(
              'h-full text-center text-sm font-medium disabled:pointer-events-auto disabled:cursor-not-allowed',
              isSubTabsSection ? subTabsStyles(tab) : tabsStyles(tab),
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
      </Tag>
    </div>
  )
}
