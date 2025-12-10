import { CSSProperties, HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

interface PageContainerProps extends HTMLAttributes<HTMLDivElement> {
  headerType: 'onlyTitle' | 'withPrimaryTabs' | 'withSubTabs' | 'withBothTabsRows'
  testId?: string
}
export const PageContainer = (props: PageContainerProps) => {
  const { headerType, children, className, testId } = props
  const tabsAndTitleHeight = 36
  const layoutPadding = 24
  const getHeaderHeight = () => {
    switch (headerType) {
      case 'onlyTitle':
        return tabsAndTitleHeight + 2 * layoutPadding
      case 'withPrimaryTabs':
      case 'withSubTabs':
        return 2 * tabsAndTitleHeight + 3 * layoutPadding
      case 'withBothTabsRows':
        return 3 * tabsAndTitleHeight + 4 * layoutPadding
    }
  }

  const headerHeight = getHeaderHeight()

  return (
    <div data-testid={testId} style={{ height: '100%', '--title-height': `${headerHeight}` } as CSSProperties}>
      <div
        className={cn(`grid w-full h-full`, className)}
        style={{ gridTemplateRows: `${headerHeight}px minmax(0, calc(100% - ${headerHeight}px))` }}
      >
        {children}
      </div>
    </div>
  )
}
