import { getTranslations } from 'next-intl/server'

import { auth } from '@/auth'
import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Item } from '@/types/common'
import { UserResponse } from '@/types/users'
import { getRequestParams, RequestParams } from '@/utils/getRequestParams'
import { mapListUsers } from '@/utils/users'

import { UsersListContent } from './components/users-list/UsersListContent'

const URL = `${process.env.API_BASE_URL}:${process.env.API_PORT}/v2`

export type UserAuthority = Item & {
  department: Item
}

type Props = {
  searchParams: Promise<RequestParams>
}

const getUserListData = async (params: URLSearchParams) => {
  const session = await auth()

  if (!session?.accessToken) {
    console.error('Unauthorized')
    return { users: [], totalCount: 0 }
  }

  try {
    const usersResponse = await fetch(`${URL}/users?${decodeURIComponent(params.toString())}`, {
      headers: {
        Authorization: `Bearer ${session?.accessToken}`,
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      cache: 'no-store',
    })

    if (!usersResponse.ok) {
      throw new Error('An error occurred while loading user list data')
    }

    const usersData = await usersResponse.json()

    const users = mapListUsers(usersData.content as UserResponse[], [])
    const totalCount = Number(usersData.totalElements) || 0

    return { users, totalCount }
  } catch (error) {
    console.error(error)
    return { users: [], totalCount: 0 }
  }
}

const UsersPage = async ({ searchParams }: Props) => {
  const t = await getTranslations('users')
  const params = await searchParams

  const { apiParams, pageSize, sort, pageIndex, search } = getRequestParams(params)

  const { users, totalCount } = await getUserListData(apiParams)
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
