'use client'

import { useTranslations } from 'next-intl'

import { ErrorPage } from '@/components/error-page/ErrorPage'

const DatasourceErrorPage = () => {
  const t = useTranslations('common')

  return <ErrorPage testId="datasourceErrorPage" title={t('errors.itemNotFound', { item: t('items.datasource') })} />
}

export default DatasourceErrorPage
