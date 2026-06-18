'use client'

import { ContentCard } from '@/components/content-card/ContentCard'
import { NoDataPage } from '@/components/no-data/no-data-page/NoDataPage'

export const DatapoolsStateCard = ({
  icon,
  title,
  subTitle,
}: {
  icon: React.ElementType
  title: string
  subTitle: string
}) => {
  const Icon = icon
  return (
    <ContentCard className="w-full">
      <div className="flex flex-col items-center gap-6 p-6 rounded-lg border border-dashed border-border">
        <div className="flex w-12 h-12 p-2 justify-center items-center gap-2 rounded-md border border-border bg-white shadow-xs">
          <Icon size={24} />
        </div>
        <NoDataPage title={title} subTitle={subTitle} className="border-0 shadow-none p-0 items-center text-center" />
      </div>
    </ContentCard>
  )
}
