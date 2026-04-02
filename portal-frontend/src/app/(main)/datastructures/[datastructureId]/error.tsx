'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatastructureErrorPage = () => {
  const t = useTranslations('common')

  return (
    <ErrorPage testId="datastructureErrorPage" title={t('errors.itemNotFound', { item: t('items.datastructure') })} />
  )
}

export default DatastructureErrorPage
