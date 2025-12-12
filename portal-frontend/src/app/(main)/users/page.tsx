import { getTranslations } from 'next-intl/server'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Item } from '@/types/common'
import { Authority, UserResponse } from '@/types/users'
import { mapListUsers } from '@/utils/users'

import { UsersListContent } from './components/users-list/UsersListContent'
import { getQueryParams } from '@/utils/getQueryParams'
import { getServerSession } from 'next-auth/next'
import { auth } from '@/auth'
import { cookies } from 'next/headers'
import { getToken } from '@auth/core/jwt'

const URL = `${process.env.API_BASE_URL}:${process.env.API_PORT}/v2`

export type UserAuthority = Item & {
  department: Item
}

type SearchParams = {
  page?: string
  pageSize?: string
  sort?: string
  order?: string
  search?: string
}

type Props = {
  searchParams: Promise<SearchParams>
}

const getUserListData = async (params: URLSearchParams) => {
  const session = await auth()

  if (!session?.accessToken) {
    console.error('Unauthorized')
    throw new Error('Unauthorized')
  }

  try {
    const usersResponse = await fetch(`${URL}/users?${params.toString()}`, {
      headers: {
        Authorization: `Bearer ${session?.accessToken}`,
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      cache: 'no-store',
    })

    if (!usersResponse) {
      throw new Error('An error occurred while loading user list data')
    }

    const usersData = await usersResponse.json()

    const users = mapListUsers(usersData.content as UserResponse[], [])
    const totalCount = Number(usersResponse.headers.get('X-Total-Count')) || 0

    return { users, totalCount }
  } catch (error) {
    console.error(error)
    throw new Error('An error occurred while loading user list data')
  }
}

const UsersPage = async ({ searchParams }: Props) => {
  const t = await getTranslations('users')
  const params = await searchParams

  // Parse search params
  const pageIndex = parseInt(params.page || '0')
  const pageSize = parseInt(params.pageSize || '10')
  const sort = params.sort || ''
  const order = params.order === 'asc' || params.order === 'desc' ? params.order : 'asc'
  const search = params.search || ''

  const apiParams = new URLSearchParams()
  apiParams.set('_page', String(pageIndex + 1))
  apiParams.set('_limit', String(pageSize))

  if (sort) {
    apiParams.set('_sort', sort)
    apiParams.set('_order', order)
  }

  if (search) {
    apiParams.set('q', search)
  }

  const { users, totalCount } = await getUserListData(apiParams)
  const totalPages = Math.ceil(totalCount / pageSize) || 1

  const sorting = sort ? [{ id: sort, desc: order === 'desc' }] : []

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
