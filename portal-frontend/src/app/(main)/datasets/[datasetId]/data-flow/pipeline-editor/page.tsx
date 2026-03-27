import { PageContainer } from '@/components/page-container/PageContainer'

import { PipelineEditorWrapper } from './_components/layout/PipelineEditorWrapper'

/**
 * Pipeline Editor Page
 *
 * Entry point for the visual pipeline editor.
 * Uses UML activity diagram style for defining data processing pipelines.
 *
 */
const PipelineEditorPage = () => {
  return (
    <PageContainer headerType="onlyTitle">
      <PipelineEditorWrapper />
    </PageContainer>
  )
}

export default PipelineEditorPage
