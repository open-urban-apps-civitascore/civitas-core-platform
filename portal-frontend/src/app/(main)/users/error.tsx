'use client'

import { useTranslations } from 'next-intl'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { ContentCard } from '@/components/content-card/ContentCard'

const UsersErrorPage = () => {
  const t = useTranslations('common')

  return (
    <PageContainer headerType="onlyTitle" testId="usersErrorPage">
      <PageHeader title={t('errors.loadingError', { item: 'User' })} />
      <PageBackground>
        <ContentCard className='p-10'>
          <p className='text-center'>{t('errors.unexpectedError')}</p>
        </ContentCard>
      </PageBackground>
    </PageContainer>
  )
}

export default UsersErrorPage
