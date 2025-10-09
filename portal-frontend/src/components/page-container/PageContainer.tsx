import { CSSProperties, HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

interface PageContainerProps extends HTMLAttributes<HTMLDivElement> {
  headerType: 'onlyTitle' | 'withPrimaryTabs' | 'withSubTabs' | 'withBothTabsRows'
}
export const PageContainer = (props: PageContainerProps) => {
  const { headerType, children, className } = props
  const titleHeight = 84
  const primaryTabsHeight = 48
  const subTabsHeight = 50
  const getHeaderHeight = () => {
    switch (headerType) {
      case 'onlyTitle':
        return titleHeight
      case 'withPrimaryTabs':
        return titleHeight + primaryTabsHeight
      case 'withSubTabs':
        return titleHeight + primaryTabsHeight
      case 'withBothTabsRows':
        return titleHeight + primaryTabsHeight + subTabsHeight
    }
  }

  const headerHeight = getHeaderHeight()

  return (
    <div style={{ height: '100%', '--title-height': `${headerHeight}` } as CSSProperties}>
      <div
        className={cn(`grid w-full h-full`, className)}
        style={{ gridTemplateRows: `${headerHeight}px minmax(0, calc(100% - ${headerHeight}px))` }}
      >
        {children}
      </div>
    </div>
  )
}
