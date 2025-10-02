'use client'

import { Row, RowSelectionState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

import { SearchField } from '@/components/searchField/SearchField'
import { TableContainer } from '@/components/table-container/TableContainer'
import { useQueryParams } from '@/hooks/useQueryParams'

import { Tab } from '@/components/page-header/components/TabsSections'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'
import { Authority, TitleSchemaType, UserResponse } from '@/types/users'
import { Plus } from 'lucide-react'
import UsersTable from './components/UsersTable'

export type ListUser = {
  id: string
  displayName: string
  authority: string
  department: string
  role: string
  email: string
  isactive: boolean
}

export const mapListUsers = (users: UserResponse[], authorities: Authority[]): ListUser[] =>
  users.map(user => {
    const authority = authorities.find(authority => user.authority?.id === authority.id)
    const department = authority?.departments.find(department => department.id === user.authority?.department?.id)
    return {
      id: user.id,
      displayName: user.displayName,
      authority: authority?.title ?? '',
      department: department?.title ?? '',
      role: user.role || '',
      email: user.email,
      isactive: user.active,
    }
  })

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
  const [selectedTab, setSelectedTab] = useState('users')
  const tabs: Tab[] = [
    {
      value: 'users',
      label: t('tabs.users'),
    },
    {
      value: 'userGroups',
      label: t('tabs.groups'),
    },
  ]

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
      const [usersResponse, authoritiesResponse] = await Promise.all([
        fetch(`${URL}/users?${params.toString()}`, {
          cache: 'no-store',
        }),
        fetch(`${URL}/authorities`, {
          cache: 'no-store',
        }),
      ])
      if (!authoritiesResponse || !usersResponse) {
        throw new Error('An error occurred while loading form data')
      }

      const [usersData, authoritiesData]: [UserResponse[], Authority[]] = await Promise.all([
        usersResponse.json(),
        authoritiesResponse.json(),
      ])

      const users = mapListUsers(usersData, authoritiesData)
      setListUsers(users)

      const totalCount = Number(usersResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
    } catch (error) {
      console.error(error)
      throw new Error('An error occurred while loading form data')
    }
  }

  useEffect(() => {
    getUserListData()
  }, [pageIndex, pageSize, URL, rowCount, sorting, search, totalPages, setApiRequestParams])

  const handleRowClick = (row: Row<ListUser>) => {
    if (row.id) {
      router.push(`users/${row.id}`, {})
    }
  }

  return (
    <div className="w-full h-full">
      <PageHeader
        title={t('title')}
        className="[--title-height:calc(--spacing(24))]"
        tabs={{ tabs: tabs, selectedTab: selectedTab, onClick: newValue => setSelectedTab(newValue) }}
        shouldShowDivider
      />
      <div className="flex justify-between items-center">
        <SearchField searchString={search} onChangeSearchString={setSearchParam} />
        <Button variant="secondary" onClick={() => router.push('/users/create')}>
          <Plus />
          {t('newUser')}
        </Button>
      </div>
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
