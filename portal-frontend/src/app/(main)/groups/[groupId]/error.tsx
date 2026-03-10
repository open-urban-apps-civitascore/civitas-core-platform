'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const GroupsErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="editGroupErrorPage" title={t('errors.itemNotFound', { item: t('items.group') })} />
}

export default GroupsErrorPage
