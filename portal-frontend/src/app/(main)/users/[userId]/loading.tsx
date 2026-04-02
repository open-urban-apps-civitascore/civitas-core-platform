import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const UserLoadingPage = async () => {
  const t = await getTranslations('common')

  return <LoadingPage testId="editUserLoadingPage" title={t('loadingItems', { item: t('items.users') })} />
}

export default UserLoadingPage
