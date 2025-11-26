'use client'

import Link from 'next/link'
import { useParams, useSearchParams } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'

const CompletionStep = () => {
  const t = useTranslations('datasets')
  const { datasetId, completionStepId } = useParams()
  const searchParams = useSearchParams()
  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t(`overview.completion.${completionStepId}`) || ''} />
      <PageBackground>
        <ContentCard>
          <Button asChild>
            <Link href={`/datasets/${datasetId}?${searchParams.toString()}`}>Back </Link>
          </Button>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}

export default CompletionStep
