import React from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'

import { NoDataPage } from '../no-data-page/NoDataPage'

interface NoDataCardProps {
  icon: React.ReactNode
  title: string
  subTitle?: string
  buttonText?: string
  onButtonClick?: () => void
  isDisabled?: boolean
  customElement?: React.ReactNode
}

export const NoDataCard = ({
  icon,
  title,
  subTitle,
  buttonText,
  onButtonClick,
  isDisabled,
  customElement,
}: NoDataCardProps) => {
  return (
    <ContentCard className="w-full">
      <div className="flex flex-col items-center gap-6 p-6 rounded-lg border border-dashed border-border">
        <div className="flex w-12 h-12 p-2 justify-center items-center gap-2 rounded-md border border-border bg-white shadow-xs">
          {icon}
        </div>
        <NoDataPage
          title={title}
          subTitle={subTitle}
          buttonText={buttonText}
          onButtonClick={onButtonClick}
          isDisabled={isDisabled}
          customElement={customElement}
          className="border-0 shadow-none p-0 items-center text-center"
        />
      </div>
    </ContentCard>
  )
}
