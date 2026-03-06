import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const GroupsLoadingPage = async () => {
  const t = await getTranslations('common')

  return <LoadingPage testId="groupsLoadingPage" title={t('loadingItems', { item: t('items.groups') })} />
}

export default GroupsLoadingPage
