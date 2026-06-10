import { Plus } from 'lucide-react'
import React, { HTMLAttributes } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface NoDataPageProps extends HTMLAttributes<HTMLDivElement> {
  title: string
  subTitle?: string
  buttonText?: string
  onButtonClick?: () => void
  isDisabled?: boolean
  customElement?: React.ReactNode
}
export const NoDataPage = (props: NoDataPageProps) => {
  const { title, subTitle, buttonText, onButtonClick, isDisabled = false, customElement, ...divProps } = props
  return (
    <ContentCard className={cn('flex flex-col justify-center items-center', divProps.className)}>
      <p className="text-xl font-semibold mb-2">{title}</p>
      {subTitle && <p className="text-[muted-foreground] mb-12">{subTitle}</p>}
      {customElement ??
        (buttonText && (
          <Button onClick={onButtonClick} type="button" disabled={isDisabled}>
            <Plus />
            {buttonText}
          </Button>
        ))}
    </ContentCard>
  )
}
