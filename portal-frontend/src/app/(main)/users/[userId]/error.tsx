'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const UsersErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="editUserErrorPage" title={t('errors.loadingError', { item: t('items.users') })} />
}

export default UsersErrorPage
