import { getTranslations } from 'next-intl/server'

import { getGroups } from '@/app/services/api/groups/serverRequests'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { TableContainer } from '@/components/table-container/TableContainer'
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
  const t = await getTranslations('groups')
  const params = await searchParams

  const { apiParams } = getApiRequestParams(params)

  const { data: groupsData, totalElements } = await getGroups(apiParams)

  const totalCount = Number(totalElements) || 0

  return (
    <PageContainer headerType="withSubTabsOrSubtitle" testId="usersPage">
      <PageHeader title={t('title')} subtitle={t('subtitle')} />
      <PageBackground>
        <TableContainer>
          <GroupsList groupsData={groupsData} totalCount={totalCount} />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default GroupsPage
