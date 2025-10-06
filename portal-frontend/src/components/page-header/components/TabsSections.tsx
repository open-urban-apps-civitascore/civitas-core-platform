'use client'

import { Button } from '@/components/ui/button'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

export type Tab = { value: string; label: string }

export interface TabSectionProps {
  tabs: Tab[]
  onClick: (tab: string) => void
  selectedTab: string
  isSubTabsSection?: boolean
  className?: string
  isHeading?: boolean
}

export const TabsSection = (props: TabSectionProps) => {
  const { tabs, onClick, selectedTab, isSubTabsSection = false, isHeading=false, className } = props

  const isMobile = useIsMobile()
  const tabsStyles = (tab: Tab) =>
    `mb-2 px-0 hover:underline hover:bg-white underline-offset-10 decoration-1 ${selectedTab === tab.value ? 'underline decoration-2' : 'text-slate-400'}`
  const subTabsStyles = (tab: Tab) =>
    `no-underline px-2 py-1 h-7 hover:bg-white ${selectedTab === tab.value && 'bg-white shadow-sm'}`
  const Tag = isHeading ? "h1" : "div"

  return (
    <Tag
      className={cn(
        `max-w-full w-auto flex justify-start gap-2 self-start cursor-pointer items-start rounded-md p-1 flex-nowrap overflow-x-auto`,
        className,
      )}
    >
      {tabs.map(tab => (
        <Button
          variant="ghost"
          key={tab.value}
          onClick={() => onClick(tab.value)}
          className={`text-center text-sm rounded-sm font-medium ${isSubTabsSection ? subTabsStyles(tab) : tabsStyles(tab)}`}
          id={`heading-${tab.value}`}
          role="tab"
          aria-selected={selectedTab === tab.value}
          tabIndex={0}
        >
          {tab.label}
        </Button>
      ))}
    </Tag>
  )
}
