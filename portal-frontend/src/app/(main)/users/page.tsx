import { getTranslations } from 'next-intl/server'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { getServerRequestHeaders } from '@/app/services/api/request/getServerRequestHeaders'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Item } from '@/types/common'
import { UserResponse } from '@/types/users'
import { getRequestParams, RequestParams } from '@/utils/getRequestParams'
import { mapListUsers } from '@/utils/users'

import { UsersListContent } from './components/users-list/UsersListContent'

export type UserAuthority = Item & {
  department: Item
}

type Props = {
  searchParams: Promise<RequestParams>
}

const UsersPage = async ({ searchParams }: Props) => {
  const t = await getTranslations('users')
  const tCommon = await getTranslations('common')
  const params = await searchParams

  const { apiParams, pageSize, sort, pageIndex, search } = getRequestParams(params)

  const { data, totalElements } = await apiRequest<UserResponse[]>({
    endpoint: '/users',
    method: 'GET',
    params: apiParams,
    headers: await getServerRequestHeaders(),
    errorMessage: 'An error occurred while fetching users.',
  })

  const users = mapListUsers(data, [])
  const totalCount = Number(totalElements) || 0
  const totalPages = Math.ceil(totalCount / pageSize) || 1

  // sorting for table as SortingState
  const sorting = sort.map((entry: string) => ({ id: entry.split(',')[0], desc: entry.split(',')[1] === 'DESC' }))

  return (
    <PageContainer headerType="onlyTitle" testId="usersPage">
      <PageHeader title={t('title')} />
      <PageBackground>
        <TableContainer>
          <UsersListContent
            users={users}
            totalCount={totalCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            search={search}
            newUserLabel={t('newUser')}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default UsersPage
