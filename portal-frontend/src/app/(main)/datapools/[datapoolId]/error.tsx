'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatapoolErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="datapoolErrorPage" title={t('errors.itemNotFound', { item: t('items.datapool') })} />
}

export default DatapoolErrorPage
