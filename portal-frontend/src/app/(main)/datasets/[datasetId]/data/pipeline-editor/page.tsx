'use client'

import { useTranslations } from 'next-intl'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

const PipelineEditorPage = () => {
  const t = useTranslations('datasets')

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('dataMode.pipelineEditor')} />
      <PageBackground>
        <div className="flex h-full items-center justify-center">
          <p className="text-muted-foreground">{t('dataMode.pipelineEditor')}</p>
        </div>
      </PageBackground>
    </PageContainer>
  )
}

export default PipelineEditorPage
