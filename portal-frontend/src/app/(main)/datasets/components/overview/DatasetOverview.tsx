'use client'

import { useTranslations } from 'next-intl'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
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
  testId?: string
}
export const DatasetOverview = (props: DatasetOverviewProps) => {
  const { dataset, dataspaces, datasources, hasMetadata, apis, groups, persistence, isEditMode, testId } = props
  const t = useTranslations('datasets')
  const tCommon = useTranslations('common')

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
      title: t('overview.completion.metadata.title'),
      isCompleted: hasMetadata,
      buttons: [{ text: t('overview.completion.metadata.button'), routeParam: 'metadata' }],
      content: hasMetadata ? <p>{t('overview.completion.metadata.requirementsMet')}</p> : undefined,
    },
    {
      title: t('overview.completion.accessPermissions.title'),
      isCompleted: groups.length > 0,
      buttons: [{ text: t('overview.completion.accessPermissions.button'), routeParam: 'usagePermissions' }],
      content: groups.length > 0 ? getList(t('overview.completion.accessPermissions.userGroups'), groups) : undefined,
    },
    {
      title: t('overview.completion.data.title'),
      isCompleted: datasources.length > 0 && persistence.length > 0,
      buttons: [{ text: t('overview.completion.data.button'), routeParam: 'data' }],
      content:
        datasources.length > 0 || persistence.length > 0 ? (
          <>
            {datasources.length > 0 && getList(t('overview.completion.data.datasources'), datasources)}
            {persistence.length > 0 && getList(t('overview.completion.data.persistence'), persistence)}
          </>
        ) : undefined,
    },
    {
      title: t('overview.completion.distribution.title'),
      isCompleted: apis.length > 0,
      buttons: [
        { text: t('overview.completion.distribution.button1'), routeParam: 'distribution', queryParam: 'type=api' },
        { text: t('overview.completion.distribution.button2'), routeParam: 'distribution', queryParam: 'type=file' },
      ],
      content: apis.length > 0 ? getList(t('overview.completion.distribution.api'), apis) : undefined,
    },
    {
      title: t('overview.completion.usagePermissions.title'),
      isCompleted: false,
      buttons: [{ text: t('overview.completion.usagePermissions.button'), routeParam: 'usagePermissions' }],
    },
    {
      title: t('overview.completion.applications.title'),
      isCompleted: false,
      buttons: [{ text: t('overview.completion.applications.button'), routeParam: 'applications' }],
    },
    {
      title: t('overview.completion.publication.title'),
      isCompleted: false,
      buttons: [{ text: t('overview.completion.publication.button'), routeParam: 'publication' }],
    },
  ]

  if (!dataset) {
    return <NoDataPage title={tCommon('noData')} />
  }

  return (
    <PageContainer
      testId={testId}
      headerType={isEditMode ? 'onlyTitle' : 'withSubTabsOrSubtitle'}
      className="overflow-hidden"
    >
      <PageHeader
        title={isEditMode ? dataset.name : t('overview.title')}
        subtitle={isEditMode ? undefined : t('overview.subtitle')}
      />
      <PageBackground className="overflow-y-auto">
        <ContentCard className={cn('h-full overflow-auto')}>
          <BaseInfoForm dataset={dataset} dataspaces={dataspaces} isEditMode={isEditMode} />
          {isEditMode && (
            <div className="max-w-300 mt-12">
              {completionSteps.map((step, index) => (
                <DetailsFieldContainer
                  key={step.title}
                  className={cn(index === completionSteps.length - 1 && 'border-b-0')}
                >
                  <CompletionStep step={step} datasetId={dataset.id} />
                </DetailsFieldContainer>
              ))}
            </div>
          )}
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
