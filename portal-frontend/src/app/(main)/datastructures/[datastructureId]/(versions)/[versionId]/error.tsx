'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatastructureVersionErrorPage = () => {
  const t = useTranslations('common')

  return (
    <ErrorPage
      testId="datastructureVersionErrorPage"
      title={t('errors.loadingError', { item: t('items.datastructureVersion') })}
    />
  )
}

export default DatastructureVersionErrorPage
