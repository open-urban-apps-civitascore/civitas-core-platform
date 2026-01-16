'use client'

import { useParams } from 'next/navigation'
import { useTranslations } from 'next-intl'

import { useGetGroup } from '@/app/services/api/groups/clientRequests'
import { ErrorPage } from '@/components/error-page/ErrorPage'
import LoadingPage from '@/components/loading-page/LoadingPage'

import GroupDetails from '../components/GroupDetails'

const UpdateGroupPage = () => {
  const params = useParams<{ groupId: string }>()
  const { groupId } = params
  const t = useTranslations('common')

  const { data: groupData, isLoading, error } = useGetGroup({ id: groupId })

  if (isLoading) {
    return <LoadingPage testId="editGroupLoadingPage" title={t('loadingItems', { item: t('items.groups') })} />
  }

  if (error || !groupData?.data) {
    return <ErrorPage testId="editGroupErrorPage" title={t('errors.loadingError', { item: t('items.groups') })} />
  }

  return <GroupDetails title={groupData?.data.title || ''} groupData={groupData?.data} isEditMode />
}

export default UpdateGroupPage
