// 'use client'
import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const UsersLoadingPage = async () => {
  const t = await getTranslations('common')

  return <LoadingPage testId="usersLoadingPage" title={t('loadingItems', { item: t('items.users') })} />
}

export default UsersLoadingPage
