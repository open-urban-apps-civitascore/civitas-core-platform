'use client'

import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Group, GroupTabProps } from '@/types/groups'
import { Authority, GroupListUser, UserResponse } from '@/types/users'
import { mapGroupListUsers } from '@/utils/users'

import UsersTable from './UsersTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

interface UsersTabProps extends GroupTabProps {
  groupData: Group
}
export const UsersTab = (props: UsersTabProps) => {
  const { groupData } = props
  const originalUsers = groupData.users
  const t = useTranslations('groups')
  const [users, setUsers] = useState<GroupListUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [rowCount, setRowCount] = useState(0)

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

  const getUserListData = async () => {
    try {
      const apiParams = setApiRequestParams()
      const idParams = originalUsers.map(user => `id=${user.id}`).join('&')
      const [usersResponse, authoritiesResponse] = await Promise.all([
        fetch(`${URL}/users?${idParams}&${apiParams}`, {
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
      const users = mapGroupListUsers(usersData, authoritiesData, originalUsers)
      setUsers(users)

      const totalCount = Number(usersResponse.headers.get('X-Total-Count')) || 0
      if (rowCount !== totalCount) {
        setRowCount(totalCount)
      }
    } catch (error) {
      console.error('An error occurred while fetching users data:', error)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    if (originalUsers.length > 0) {
      getUserListData()
    } else {
      setIsLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, rowCount, sorting, search, totalPages, setApiRequestParams])

  const CustomElement = (
    <Button onClick={() => {}}>
      <Plus />
      {t('users.assign')}
    </Button>
  )
  if (users.length === 0 && !isLoading) {
    return <NoDataPage title={t('users.noUsers')} subTitle={t('users.noUsersSub')} buttonText={t('users.assign')} />
  }

  return (
    <div>
      <SearchHeader searchString={search} onChangeSearchString={setSearchParam} customElement={CustomElement} />
      <UsersTable
        users={users}
        rowCount={rowCount}
        pageIndex={pageIndex}
        pageSize={pageSize}
        sorting={sorting}
        totalPages={totalPages}
        onSortingChange={setSortingParams}
        onPaginationChange={setPaginationParams}
        isLoading={isLoading}
      />
    </div>
  )
}
