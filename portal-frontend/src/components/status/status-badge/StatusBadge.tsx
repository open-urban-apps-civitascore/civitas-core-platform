import { CircleCheck, CircleCheckBig, CircleDashed, LucideIcon } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { STATUS_TYPES, StatusTypes } from '@/types/common'

interface StatusBadgeProps {
  status: StatusTypes
  count?: number
  className?: string
  testId?: string
}

export const STATUS_ICONS: Record<StatusTypes, LucideIcon> = {
  [STATUS_TYPES.DRAFT]: CircleDashed,
  [STATUS_TYPES.READY]: CircleCheck,
  [STATUS_TYPES.AVAILABLE]: CircleCheckBig,
}

export const StatusBadge = (props: StatusBadgeProps) => {
  const { status, count, className, testId = 'statusBadge' } = props
  const t = useTranslations('common')
  const Icon = STATUS_ICONS[status]
  const label = count === undefined ? t(`status.${status}`) : t(`statusCount.${status}`, { count })

  return (
    <Badge
      variant="outline"
      data-testid={testId}
      className={cn('h-7 gap-2 px-3 text-sm font-normal [&>svg]:size-4', className)}
    >
      <Icon className="text-muted-foreground" aria-hidden />
      {label}
    </Badge>
  )
}
