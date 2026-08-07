import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const DatastructureVersionLoadingPage = async () => {
  const t = await getTranslations('common')

  return (
    <LoadingPage testId="versionLoadingPage" title={t('loadingItems', { item: t('items.datastructureVersion') })} />
  )
}

export default DatastructureVersionLoadingPage
