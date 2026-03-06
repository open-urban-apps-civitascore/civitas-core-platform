import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const GroupLoadingPage = async () => {
  const t = await getTranslations('common')

  return <LoadingPage testId="editGroupsLoadingPage" title={t('loadingItems', { item: t('items.group') })} />
}

export default GroupLoadingPage
