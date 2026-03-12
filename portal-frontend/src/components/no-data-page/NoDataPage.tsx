import { Plus } from 'lucide-react'
import React, { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

import { ContentCard } from '../content-card/ContentCard'
import { Button } from '../ui/button'

interface NoDataPageProps extends HTMLAttributes<HTMLDivElement> {
  title: string
  subTitle?: string
  buttonText?: string
  onButtonClick?: () => void
  isDisabled?: boolean
}
export const NoDataPage = (props: NoDataPageProps) => {
  const { title, subTitle, buttonText, onButtonClick, isDisabled = false, ...divProps } = props
  return (
    <ContentCard className={cn('flex flex-col justify-center items-center', divProps.className)}>
      <p className="text-xl font-semibold mb-2">{title}</p>
      {subTitle && <p className="text-[muted-foreground] mb-12">{subTitle}</p>}
      {buttonText && (
        <Button onClick={onButtonClick} type="button" disabled={isDisabled}>
          <Plus />
          {buttonText}
        </Button>
      )}
    </ContentCard>
  )
}
