'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageBackground } from '@/components/page-background/PageBackground'
import { PageContainer } from '@/components/page-container/PageContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Authority, Category, Role, UserResponse } from '@/types/users'
import { mapListUsers } from '@/utils/users'

import UsersTable from './components/UsersTable'

export type UserAuthority = Category & {
  department: Category
}

export type ListUser = {
  id: string
  displayName: string
  authority: string
  department: string
  roles: string[]
  email: string
  isactive: boolean
}

export const getSortParam = (sorting: SortingState) => {
  if (sorting.length > 0) {
    const sortingId = sorting[0]?.id
    const sortParam = `&_sort=${sortingId}`
    const orderParam = sorting[0]?.desc ? `&_order=desc` : `&_order=asc`
    return `${sortParam}${orderParam}`
  }
  return ''
}

const UsersPage = () => {
  const t = useTranslations('users')
  const router = useRouter()
  const [listUsers, setListUsers] = useState<ListUser[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const [isLoading, setIsLoading] = useState(true)

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    setApiRequestParams,
    pageIndex,
    pageSize,
    sorting,
    search,
  } = useQueryParams()

  const totalPages = Math.ceil(rowCount / pageSize)

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  const getUserListData = async () => {
    const params = setApiRequestParams(totalPages)

    try {
      setIsLoading(true)
      const [usersResponse, authoritiesResponse, rolesResponse] = await Promise.all([
        fetch(`${URL}/users?${params.toString()}`, {
          cache: 'no-store',
        }),
        fetch(`${URL}/authorities`, {
          cache: 'no-store',
        }),
        fetch(`${URL}/roles`, {
          cache: 'no-store',
        }),
      ])
      if (!authoritiesResponse || !usersResponse) {
        throw new Error('An error occurred while loading form data')
      }

      const [usersData, authoritiesData, rolesData]: [UserResponse[], Authority[], Role[]] = await Promise.all([
        usersResponse.json(),
        authoritiesResponse.json(),
        rolesResponse.json(),
      ])

      const rolesMap: Record<string, string> = {}
      rolesData.forEach((role: Role) => {
        rolesMap[role.id] = role.name
      })

      const users = mapListUsers(usersData, authoritiesData, rolesMap)
      setListUsers(users)
      setIsLoading(false)

      const totalCount = Number(usersResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
    } catch (error) {
      console.error(error)
      setIsLoading(false)
      throw new Error('An error occurred while loading form data')
    }
  }

  useEffect(() => {
    getUserListData()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, URL, rowCount, sorting, search, totalPages, setApiRequestParams])

  const handleRowClick = (row: Row<ListUser>) => {
    if (row.id) {
      const params = setApiRequestParams(totalPages)
      router.push(`users/${row.id}?${params}`, {})
    }
  }

  const CustomElement = (
    <Button onClick={() => router.push('/users/create')}>
      <Plus />
      {t('newUser')}
    </Button>
  )

  return (
    <PageContainer headerType="onlyTitle">
      <PageHeader title={t('title')} />
      <PageBackground>
        <SearchHeader customElement={CustomElement} onChangeSearchString={setSearchParam} searchString={search} />
        <TableContainer>
          <UsersTable
            users={listUsers}
            rowCount={rowCount}
            pageIndex={pageIndex}
            pageSize={pageSize}
            sorting={sorting}
            totalPages={totalPages}
            rowSelection={rowSelection}
            setRowSelection={setRowSelection}
            onRowClick={handleRowClick}
            onSortingChange={setSortingParams}
            onPaginationChange={setPaginationParams}
            isLoading={isLoading}
          />
        </TableContainer>
      </PageBackground>
    </PageContainer>
  )
}

export default UsersPage
