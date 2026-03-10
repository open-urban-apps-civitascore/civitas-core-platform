'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatastructuresErrorPage = () => {
  const t = useTranslations('common')

  return (
    <ErrorPage testId="datastructuresErrorPage" title={t('errors.loadingError', { item: t('items.datastructures') })} />
  )
}

export default DatastructuresErrorPage
