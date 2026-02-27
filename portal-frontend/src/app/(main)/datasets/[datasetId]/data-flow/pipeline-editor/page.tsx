import { getTranslations } from 'next-intl/server'

import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { PipelineEditorWrapper } from './_components/layout/PipelineEditorWrapper'

/**
 * Pipeline Editor Page
 *
 * Entry point for the visual pipeline editor.
 * Uses UML activity diagram style for defining data processing pipelines.
 *
 */
const PipelineEditorPage = async () => {
  const t = await getTranslations('datasets')

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('dataMode.pipelineEditor')} />
      <div className="h-[calc(100vh-12rem)] w-full overflow-hidden rounded-xl border bg-background">
        <PipelineEditorWrapper />
      </div>
    </PageContainer>
  )
}

export default PipelineEditorPage
