'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'
import { TableContainer } from '@/components/table-container/TableContainer'
import { useQueryParams } from '@/hooks/useQueryParams'

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
  role: string
}

export type ListUser = {
  id: string
  displayName: string
  authority: string
  department: string
  role: string
  email: string
  isactive: boolean
}

export type FormUser = UserResponse

export const mapListUsers = (users: UserResponse[]): ListUser[] =>
  users.map(user => ({
    id: user.id,
    displayName: user.displayName,
    authority: user.authority,
    department: user.department,
    role: user.role,
    email: user.email,
    isactive: user.active,
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

  useEffect(() => {
    const params = setApiRequestParams(totalPages)
    const getUsers = async () => {
      try {
        const usersResponse = await fetch(`${URL}/users?${params.toString()}`)
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
  }, [pageIndex, pageSize, URL, rowCount, sorting, search, totalPages, setApiRequestParams])

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
          pageIndex={pageIndex}
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
