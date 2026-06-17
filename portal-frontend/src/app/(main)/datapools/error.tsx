'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatapoolsErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="datapoolsErrorPage" title={t('errors.itemNotFound', { item: t('items.datapools') })} />
}

export default DatapoolsErrorPage
