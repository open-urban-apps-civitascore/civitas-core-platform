'use client'

import { Button } from '@/components/ui/button'
import { useIsMobile } from '@/hooks/use-mobile'

import { BasePageHeaderProps } from '../PageHeader'

export interface TabHeaderProps extends BasePageHeaderProps {
  isTabHeader: true
  tabs: { value: string; label: string }[]
  onClick: (tab: string) => void
  selectedTab: string
}

export const TabsSection = (props: TabHeaderProps) => {
  const { tabs, onClick, selectedTab } = props

  const isMobile = useIsMobile()

  return (
    <h1 className={`flex ${isMobile ? 'flex-col' : 'flex-row gap-2'} justify-start  cursor-pointer items-start`}>
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
