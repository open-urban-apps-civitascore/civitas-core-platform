import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const UsersErrorPage = async () => {
  const t = await getTranslations('common')

  return <LoadingPage testId="editUserLoadingPage" title={t('loadingItems', { item: t('items.group') })} />
}

export default UsersErrorPage
