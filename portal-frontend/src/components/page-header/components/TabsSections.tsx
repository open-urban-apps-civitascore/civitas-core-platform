'use client'

import { Button, ButtonProps } from '@/components/ui/button'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'

export type Tab = { value: string; label: string }

export interface TabSectionProps {
  tabs: Tab[]
  onClick: (tab: string) => void
  selectedTab: string
  className?: string
}

export const TabsSection = (props: TabSectionProps) => {
  const { tabs, onClick, selectedTab, className } = props

  const isMobile = useIsMobile()

  return (
    <h1
      className={cn(
        `flex ${isMobile ? 'flex-col' : 'flex-row gap-2'} justify-start  cursor-pointer items-start rounded-md`,
        className,
      )}
    >
      {tabs.map(tab => (
        <Button
          variant="ghost"
          key={tab.value}
          onClick={() => onClick(tab.value)}
          className={`m-0 px-2 text-center font-medium  ${isMobile ? 'text-lg' : 'text-xl'} ${selectedTab === tab.value ? 'underline' : 'text-slate-400'}`}
          id={`heading-${tab.value}`}
          role="tab"
          aria-selected={selectedTab === tab.value}
          tabIndex={0}
        >
          {tab.label}
        </Button>
      ))}
    </h1>
  )
}
