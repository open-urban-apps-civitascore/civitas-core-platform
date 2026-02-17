import { getTranslations } from 'next-intl/server'

import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

import { PipelineEditorWrapper } from './pipeline-editor/_components/layout/PipelineEditorWrapper'

const DataModePage = async () => {
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

export default DataModePage

/* DISABLED FEATURE -> TABLE EDITOR - Original DataModePage with mode selection
import Link from 'next/link'
import { getTranslations } from 'next-intl/server'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'

type PageProps = {
  params: Promise<{ datasetId: string }>
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}

const DataModePage = async ({ params, searchParams }: PageProps) => {
  const t = await getTranslations('datasets')
  const { datasetId } = await params
  const resolvedSearchParams = await searchParams

  const searchParamsString = new URLSearchParams(
    Object.entries(resolvedSearchParams)
      .filter(([, value]) => value !== undefined)
      .map(([key, value]) => [key, Array.isArray(value) ? value[0] : value] as [string, string]),
  ).toString()

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('overview.completion.data.title')} />
      <PageBackground>
        <div className="flex h-full flex-col items-center justify-center gap-8">
          <p className="mb-4 text-lg font-medium">{t('dataMode.selectMode')}</p>
          <div className="flex gap-16">
            <div className="flex max-w-sm flex-col items-end gap-4">
              <Button
                asChild
                variant="outline"
                className="border-2 border-[#F59E0B] text-[#F59E0B] hover:bg-[#F59E0B]/10"
              >
                <Link
                  href={`/datasets/${datasetId}/data/table-editor${searchParamsString ? `?${searchParamsString}` : ''}`}
                >
                  {t('dataMode.tableEditor')}
                </Link>
              </Button>
              <p className="mt-2 text-right text-sm text-muted-foreground">{t('dataMode.tableDescription')}</p>
            </div>
            <div className="flex max-w-sm flex-col items-start gap-4">
              <Button
                asChild
                variant="outline"
                className="border-2 border-[#22C55E] text-[#22C55E] hover:bg-[#22C55E]/10"
              >
                <Link
                  href={`/datasets/${datasetId}/data/pipeline-editor${searchParamsString ? `?${searchParamsString}` : ''}`}
                >
                  {t('dataMode.pipelineEditor')}
                </Link>
              </Button>
              <p className="mt-2 text-left text-sm text-muted-foreground">{t('dataMode.pipelineDescription')}</p>
            </div>
          </div>
        </div>
      </PageBackground>
    </PageContainer>
  )
}

export default DataModePage
*/
