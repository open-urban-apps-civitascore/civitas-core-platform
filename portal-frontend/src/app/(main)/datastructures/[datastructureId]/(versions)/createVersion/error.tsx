'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatastructureVersionCreateErrorPage = () => {
  const t = useTranslations('common')

  return (
    <ErrorPage
      testId="datastructureVersionCreateErrorPage"
      title={t('errors.loadingError', { item: t('items.data') })}
    />
  )
}

export default DatastructureVersionCreateErrorPage
