import React from 'react'

import { BasicTooltip } from '@/components/tooltip/Tooltip'
import { Badge } from '@/components/ui/badge'

interface BadgesWithTooltipProps {
  items: string[]
  minVisibleBadges?: number
}

export const BadgesWithTooltip = (props: BadgesWithTooltipProps) => {
  const { items, minVisibleBadges = 1 } = props
  if (!items || items.length === 0) return '-'
  const tooltipContent = (
    <div className="flex flex-wrap gap-2">
      {items.slice(minVisibleBadges, items.length).map((item, i) => (
        // eslint-disable-next-line react/no-array-index-key
        <Badge key={`${item}-${i}`} variant="secondary">
          {item}
        </Badge>
      ))}
    </div>
  )
  const badges = (
    <>
      {items.slice(0, minVisibleBadges).map((item, i) => (
        // eslint-disable-next-line react/no-array-index-key
        <Badge key={`${item}-${i}`} variant="secondary">
          {item}
        </Badge>
      ))}
      {items.length > minVisibleBadges && (
        <BasicTooltip tooltipContent={tooltipContent}>
          <Badge variant="outline">+{items.length - minVisibleBadges}</Badge>
        </BasicTooltip>
      )}
    </>
  )
  return <div className="flex flex-wrap gap-1">{badges}</div>
}
