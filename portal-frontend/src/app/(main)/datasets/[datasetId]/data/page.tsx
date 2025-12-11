'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'

const DataModePage = () => {
  const t = useTranslations('datasets')
  const { datasetId } = useParams()
  const searchParams = useSearchParams()

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
                <Link href={`/datasets/${datasetId}/data/table-editor?${searchParams.toString()}`}>
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
                <Link href={`/datasets/${datasetId}/data/pipeline-editor?${searchParams.toString()}`}>
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
