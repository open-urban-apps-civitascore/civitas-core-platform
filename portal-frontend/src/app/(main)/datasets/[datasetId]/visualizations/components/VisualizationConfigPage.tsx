'use client'

import { useTranslations } from 'next-intl'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Dataset } from '@/types/datasets'
import { VisualizationTypeQuery } from '@/types/visualizations'

interface VisualizationConfigPageProps {
  dataset: Dataset
  visualizationType: VisualizationTypeQuery
  testId?: string
}

export const VisualizationConfigPage = ({ dataset, testId }: VisualizationConfigPageProps) => {
  const t = useTranslations('datasets.overview.completion.visualizations')

  return (
    <PageContainer testId={testId} headerType="onlyTitle" className="overflow-auto">
      <PageHeader title={dataset.name} />
      <PageBackground>
        <p className="text-sm text-muted-foreground">{t('title')}</p>
      </PageBackground>
    </PageContainer>
  )
}
