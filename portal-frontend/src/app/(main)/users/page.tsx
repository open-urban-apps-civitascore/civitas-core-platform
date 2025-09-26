'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'
import { TableContainer } from '@/components/table-container/TableContainer'
import { SEARCH_PARAMS } from '@/const/searchParams'
import { useRouteParams } from '@/hooks/useSearchParams'

import UsersTable from './components/UsersTable'

export type UserGroup = {
  id: string
  title: string
}

export type UserResponse = {
  id: string
  firstName: string
  lastName: string
  displayName: string
  email: string
  phone: string
  title: string
  authority: string
  department: string
  group: UserGroup
  active: boolean
}

export type ListUser = {
  id: string
  displayName: string
  email: string
  group: UserGroup
}

export type FormUser = UserResponse

export const mapListUsers = (users: UserResponse[]): ListUser[] =>
  users.map(user => ({
    id: user.id,
    displayName: user.displayName,
    email: user.email,
    group: user.group,
  }))

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

  const { setSortingParams, setPaginationParams, setSearchParam, pageIndex, pageSize, sorting, search } =
    useRouteParams()
  const totalPages = Math.ceil(rowCount / pageSize)

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  useEffect(() => {
    const apiParams = new URLSearchParams()
    apiParams.set(SEARCH_PARAMS.pageIndex, String(pageIndex))
    if (totalPages > 0 && pageIndex + 1 > totalPages) {
      setPaginationParams({ pageIndex: totalPages - 1, pageSize: pageSize })
    }
    apiParams.set(SEARCH_PARAMS.pageSize, String(pageSize))
    if (sorting[0]) {
      apiParams.set(SEARCH_PARAMS.sortingId, sorting[0].id)
      apiParams.set(SEARCH_PARAMS.order, sorting[0].desc ? 'desc' : 'asc')
    }
    if (search) {
      apiParams.set(SEARCH_PARAMS.search, search)
    }
    const getUsers = async () => {
      try {
        const usersResponse = await fetch(`${URL}/users?${apiParams.toString()}`)
        const usersData: UserResponse[] = await usersResponse.json()
        const users = mapListUsers(usersData)
        setListUsers(users)
        const totalCount = Number(usersResponse.headers.get('X-Total-Count')) || 0
        if (rowCount !== totalCount) {
          setRowCount(totalCount)
        }
      } catch (error) {
        console.error(error)
      }
    }
    getUsers()
  }, [pageIndex, pageSize, URL, rowCount, sorting, search])

  const handleRowClick = (row: Row<ListUser>) => {
    if (row.id) {
      router.push(`users/${row.id}`, {})
    }
  }

  return (
    <div className="w-full h-full">
      <PageHeader title={t('title')} />
      <SearchField searchString={search} onChangeSearchString={setSearchParam} />
      <TableContainer>
        <UsersTable
          users={listUsers}
          rowCount={rowCount}
          pageIndex={pageIndex - 1}
          pageSize={pageSize}
          sorting={sorting}
          totalPages={totalPages}
          rowSelection={rowSelection}
          setRowSelection={setRowSelection}
          onRowClick={handleRowClick}
          onSortingChange={setSortingParams}
          onPaginationChange={setPaginationParams}
        />
      </TableContainer>
    </div>
  )
}

export default UsersPage
