'use client'

import { SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { GridContainer } from '@/components/grid-container/GridContainer'
import { PageHeader } from '@/components/page-header/PageHeader'
import { SearchField } from '@/components/searchField/SearchField'

import UsersTable from './components/UsersTable'

export type UserResponse = {
  id: string
  firstName: string
  lastName: string
  displayName: string
  email: string
}

export type User = {
  id: string
  firstName: string
  lastName: string
  displayName: string
  email: string
}

export const mapUsers = (users: UserResponse[]): User[] =>
  users.map(user => ({
    id: user.id,
    firstName: user.firstName,
    lastName: user.lastName,
    displayName: user.displayName,
    email: user.email,
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
  const [users, setUsers] = useState<User[]>([])
  const [rowCount, setRowCount] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [pageIndex, setPageIndex] = useState(0)
  const [sorting, setSorting] = useState<SortingState>([])
  const [searchString, setSearchString] = useState('')
  const totalPages = Math.ceil(rowCount / pageSize)

  const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

  // adjusts the pageIndex when filtering reduces the totalPages and the user is on a page beyond that limit
  useEffect(() => {
    if (totalPages > 0 && pageIndex + 1 > totalPages) {
      setPageIndex(totalPages - 1)
    }
  }, [totalPages, pageIndex])

  useEffect(() => {
    const sortParams = getSortParam(sorting)
    const searchParam = getSearchParam(searchString)
    const getUsers = async () => {
      try {
        const usersResponse = await fetch(
          `${URL}/users?_page=${pageIndex + 1}&_limit=${pageSize}${sortParams}${searchParam}`,
        )
        const usersData: UserResponse[] = await usersResponse.json()
        const users = mapUsers(usersData)
        setUsers(users)
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

  return (
    <div className="w-full h-full">
      <PageHeader title={t('title')} />
      <SearchField setSearchString={setSearchString} />
      <GridContainer columns={2}>
        <UsersTable
          users={users}
          rowCount={rowCount}
          pageIndex={pageIndex}
          setPageIndex={setPageIndex}
          pageSize={pageSize}
          setPageSize={setPageSize}
          sorting={sorting}
          setSorting={setSorting}
          totalPages={totalPages}
        />
        <form>
          <div>Form placeholder</div>
        </form>
      </GridContainer>
    </div>
  )
}

export default UsersPage
