'use client'

import { Link } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

interface InUseIndicatorProps {
  isInUseByReleased: boolean
  className?: string
  testId?: string
}

const ICON_SIZE = 16

export const InUseIndicator = (props: InUseIndicatorProps) => {
  const { isInUseByReleased, className, testId = 'inUseIndicator' } = props
  const t = useTranslations('common.inUse')

  if (!isInUseByReleased) return null

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span
          data-testid={testId}
          role="img"
          aria-label={t('tooltip')}
          tabIndex={0}
          className={cn(
            'inline-flex size-9 shrink-0 items-center justify-center rounded-md border border-input bg-background text-muted-foreground outline-none focus-visible:ring-ring/50 focus-visible:ring-[3px]',
            className,
          )}
        >
          <Link size={ICON_SIZE} aria-hidden />
        </span>
      </TooltipTrigger>
      <TooltipContent>{t('tooltip')}</TooltipContent>
    </Tooltip>
  )
}
