'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const UsersErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="editUserErrorPage" title={t('errors.itemNotFound', { item: t('items.user') })} />
}

export default UsersErrorPage
