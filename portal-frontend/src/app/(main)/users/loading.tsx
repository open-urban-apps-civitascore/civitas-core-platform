'use client'

import { useTranslations } from 'next-intl'

import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'

const UsersErrorPage = () => {
  const t = useTranslations('common')

  return (
    <PageContainer headerType="onlyTitle" testId="usersErrorPage">
      <PageHeader title={t('loadingItems', { item: t('items.users') })} />
      <PageBackground>
        <ContentCard className="p-10">
          <LoadingSpinner />
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}

export default UsersErrorPage
