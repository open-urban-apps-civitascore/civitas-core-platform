'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { TableContainer } from '@/components/table-container/TableContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'

import { UserForm } from './[userId]/UserForm'
import UsersTable from './components/UsersTable'
import { useRouter } from 'next/navigation'

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

export const getSearchParam = (searchString: string) => (searchString ? `&q=${searchString}` : '')

const UsersPage = () => {
  const t = useTranslations('users')
  const router = useRouter()
  const [listUsers, setListUsers] = useState<ListUser[]>([])
  const [userResponse, setUserResponse] = useState<UserResponse[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [pageIndex, setPageIndex] = useState(0)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const totalPages = Math.ceil(rowCount / pageSize)
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({})
  const rowSelectionIndex = Object.keys(rowSelection)[0]

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  // adjusts the pageIndex when filtering reduces the totalPages and the user is on a page beyond that limit
  useEffect(() => {
    if (totalPages > 0 && pageIndex + 1 > totalPages) {
      setPageIndex(totalPages - 1)
    }
  }, [totalPages, pageIndex])

  const selectedUser = useMemo(() => {
    if (userResponse.length > 0 && rowSelectionIndex) {
      return userResponse.find(user => user.id === rowSelectionIndex)
    } else return null
  }, [rowSelectionIndex])

  useEffect(() => {
    const sortParams = getSortParam(sorting)
    const searchParam = getSearchParam(searchString)
    const getUsers = async () => {
      try {
        const usersResponse = await fetch(
          `${URL}/users?_page=${pageIndex + 1}&_limit=${pageSize}${sortParams}${searchParam}`,
        )
        const usersData: UserResponse[] = await usersResponse.json()
        setUserResponse(usersData)
        if (usersData.length > 0 && !rowSelectionIndex) {
          setRowSelection({ [usersData[0].id]: true })
        }
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
  }, [pageIndex, pageSize, URL, rowCount, sorting, searchString])

  const handleRowClick = (row: Row<ListUser>) => {
    if (row.id) {
      router.push(`users/${row.id}`, {})
    }
  }

  return (
    <div className="w-full h-full">
      <PageHeader title={t('title')} />
      <SearchField setSearchString={setSearchString} />
      <TableContainer>
        <UsersTable
          users={listUsers}
          rowCount={rowCount}
          pageIndex={pageIndex}
          setPageIndex={setPageIndex}
          pageSize={pageSize}
          setPageSize={setPageSize}
          sorting={sorting}
          setSorting={setSorting}
          totalPages={totalPages}
          rowSelection={rowSelection}
          setRowSelection={setRowSelection}
          onRowClick={handleRowClick}
        />
      </TableContainer>
    </div>
  )
}

export default UsersPage
