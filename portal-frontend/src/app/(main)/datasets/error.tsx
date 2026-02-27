'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatasetsErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="datasetsErrorPage" title={t('errors.itemNotFound', { item: t('items.datasets') })} />
}

export default DatasetsErrorPage
