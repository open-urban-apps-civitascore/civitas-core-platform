'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const GroupsErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="groupsErrorPage" title={t('errors.loadingError', { item: t('items.groups') })} />
}

export default GroupsErrorPage
