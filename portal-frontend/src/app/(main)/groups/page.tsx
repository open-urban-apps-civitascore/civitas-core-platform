import { getGroups } from '@/app/services/api/groups/serverRequests'
import { Item } from '@/types/common'
import { ApiRequestParams, getApiRequestParams } from '@/utils/requestParams'

import GroupsList from './components/groups-list/GroupList'

export type UserAuthority = Item & {
  department: Item
}

type Props = {
  searchParams: Promise<ApiRequestParams>
}

const GroupsPage = async ({ searchParams }: Props) => {
  const params = await searchParams
  const { apiParams } = getApiRequestParams(params)

  const { data: groupsData, totalElements } = await getGroups(apiParams)

  const totalCount = Number(totalElements) || 0

  return <GroupsList groupsData={groupsData} totalCount={totalCount} />
}

export default GroupsPage
