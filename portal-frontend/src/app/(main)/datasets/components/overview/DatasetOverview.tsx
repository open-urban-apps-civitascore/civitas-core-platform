'use client'

import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { cn } from '@/lib/utils'
import { SelectOption } from '@/types/common'
import { CompletionStepData, DatasetFormData } from '@/types/datasets'

import { BaseInfoForm } from './BaseInfoForm'
import { CompletionStep } from './CompletionStep'

interface DatasetOverviewProps {
  dataset: DatasetFormData
  hasMetadata: boolean
  datasources: string[]
  apis: string[]
  persistence: string[]
  groups: string[]
  dataspaces: SelectOption[]
  isEditMode: boolean
}
export const DatasetOverview = (props: DatasetOverviewProps) => {
  const { dataset, dataspaces, datasources, hasMetadata, apis, groups, persistence, isEditMode } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')
  const [isLoading, setIsLoading] = useState(false)

  const getList = (title: string, items: string[]) => (
    <div className="w-[50%] grid grid-cols-2">
      <h4>{title}</h4>
      <ul className="list-none">
        {items.map(item => (
          <li key={item} className="font-normal">
            {item}
          </li>
        ))}
      </ul>
    </div>
  )

  const completionSteps: CompletionStepData[] = [
    {
      key: 'metadata',
      isCompleted: hasMetadata,
      buttons: 1,
      content: hasMetadata ? <p>{t('overview.completion.metadata.requirementsMet')}</p> : undefined,
    },
    {
      key: 'accessPermissions',
      isCompleted: groups.length > 0,
      buttons: 1,
      content: groups.length > 0 ? getList(t('overview.completion.accessPermissions.userGroups'), groups) : undefined,
    },
    {
      key: 'data',
      isCompleted: datasources.length > 0 && persistence.length > 0,
      buttons: 1,
      content:
        datasources.length > 0 || persistence.length > 0 ? (
          <>
            {datasources.length > 0 && getList(t('overview.completion.data.datasources'), datasources)}
            {persistence.length > 0 && getList(t('overview.completion.data.persistence'), persistence)}
          </>
        ) : undefined,
    },
    {
      key: 'distribution',
      isCompleted: apis.length > 0,
      buttons: 2,
      content: apis.length > 0 ? getList(t('overview.completion.distribution.api'), apis) : undefined,
    },
    { key: 'usagePermissions', isCompleted: false, buttons: 1 },
    { key: 'applications', isCompleted: false, buttons: 1 },
    { key: 'publication', isCompleted: false, buttons: 1 },
  ]

  if (!dataset) {
    return <NoDataPage title={tCommon('noData')} />
  }

  if (isLoading) {
    return <LoadingSpinner title="Loading..." />
  }

  return (
    <PageContainer headerType={isEditMode ? 'onlyTitle' : 'withSubTabsOrSubtitle'} className="overflow-hidden">
      <PageHeader
        title={isEditMode ? dataset.name : t('overview.title')}
        subtitle={isEditMode ? undefined : t('overview.subtitle')}
      />
      <PageBackground className="overflow-y-auto">
        <ContentCard className={cn('h-full overflow-auto')}>
          <BaseInfoForm dataset={dataset} dataspaces={dataspaces} isEditMode={isEditMode} setIsLoading={setIsLoading} />
          <div className="mt-12">
            {completionSteps.map((step, index) => (
              <DetailsFieldContainer
                key={step.key}
                className={cn(index === completionSteps.length - 1 && 'border-b-0')}
              >
                <CompletionStep step={step} datasetId={dataset.id} disabled={!isEditMode} />
              </DetailsFieldContainer>
            ))}
          </div>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
