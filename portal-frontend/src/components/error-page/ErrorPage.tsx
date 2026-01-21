'use client'

import { useTranslations } from 'next-intl'

import { ContentCard } from '@/components/content-card/ContentCard'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

interface ErrorPageProps {
  title: string
  errorText?: string
  testId?: string
}

export const ErrorPage = (props: ErrorPageProps) => {
  const { title, errorText, testId } = props
  const t = useTranslations()

  return (
    <PageContainer headerType="onlyTitle" testId={testId}>
      <PageHeader title={title || t('common.errors.loadingError', { item: t('users.user') })} />
      <PageBackground>
        <ContentCard className="p-10">
          <p className="text-center">{errorText || t('common.errors.unexpectedError')}</p>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}
