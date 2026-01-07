'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const UsersErrorPage = () => {
  const t = useTranslations()

  return <ErrorPage title={t('common.errors.loadingError', { item: t('users.user') })} />
}

export default UsersErrorPage
