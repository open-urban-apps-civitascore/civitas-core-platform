import React from 'react'

import { cn } from '@/lib/utils'

import { Badge } from '../ui/badge'

interface ActivityBadgeProps {
  isActive: boolean | undefined
  title: string
}

export const ActivityBadge = (props: ActivityBadgeProps) => {
  const { isActive, title } = props
  let bgColor = 'bg-secondary'
  let textColor = 'text-foreground'
  switch (isActive) {
    case false:
      bgColor = 'bg-red-600/10'
      textColor = 'text-red-700'
      break
    case true:
      bgColor = 'bg-green-600/10'
      textColor = 'text-green-800'
      break
    default:
      break
  }
  return (
    <Badge className={cn(bgColor, textColor)} variant="secondary">
      {title}
    </Badge>
  )
}
