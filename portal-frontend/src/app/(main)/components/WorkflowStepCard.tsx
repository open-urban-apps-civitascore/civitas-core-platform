import Image from 'next/image'
import { ReactNode } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { cn } from '@/lib/utils'

export interface WorkflowStepCardProps {
  stepNumber: number
  title: ReactNode
  subtitle: string
  illustrationSrc: string
  illustrationWidth: number
  illustrationHeight: number
  className?: string
}

export const WorkflowStepCard = (props: WorkflowStepCardProps) => {
  const { stepNumber, title, subtitle, illustrationSrc, illustrationWidth, illustrationHeight, className } = props

  return (
    <ContentCard className={cn('relative flex-col items-start justify-start gap-4', className)}>
      <span
        aria-hidden="true"
        className="absolute top-4 -left-3 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground"
      >
        {stepNumber}
      </span>
      <div className="flex flex-col text-left">
        <p className="text-sm font-semibold sm:text-base">{title}</p>
        <p className="text-xs text-muted-foreground sm:text-sm">{subtitle}</p>
      </div>
      <div className="flex w-full flex-1 items-center justify-center">
        <Image
          src={illustrationSrc}
          alt=""
          width={illustrationWidth}
          height={illustrationHeight}
          className="h-auto max-w-full"
        />
      </div>
    </ContentCard>
  )
}
