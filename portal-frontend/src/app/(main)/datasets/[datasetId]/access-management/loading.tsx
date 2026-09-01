import { getTranslations } from 'next-intl/server'

import LoadingPage from '@/components/loading-page/LoadingPage'

const AssignmentsLoadingPage = async () => {
  const t = await getTranslations('common')

  return <LoadingPage testId="assignmentsLoadingPage" title={t('loadingItems', { item: t('items.assignments') })} />
}

export default AssignmentsLoadingPage
