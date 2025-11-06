'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useEffect, useState } from 'react'

import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Group, GroupTabProps } from '@/types/groups'
import { Authority, GroupListUser, UserResponse } from '@/types/users'
import { mapGroupListUsers } from '@/utils/users'

import { patchGroupUsers } from '../../actions'
import { AssignUsersModal } from './AssignUsersModal'
import UsersTable from './UsersTable'

const URL = `${process.env.NEXT_PUBLIC_JSON_SERVER_HOST}:${process.env.NEXT_PUBLIC_JSON_SERVER_PORT}`

interface UsersTabProps extends GroupTabProps {
  groupData: Group
}
export const UsersTab = (props: UsersTabProps) => {
  const { groupData } = props
  const router = useRouter()
  const originalUsers = groupData.users
  const t = useTranslations('groups')
  const [users, setUsers] = useState<GroupListUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [rowCount, setRowCount] = useState(0)
  const [isAssignUsersOpen, setIsAssignUsersOpen] = useState(false)
  const [isUpdatingGroupUsers, setIsUpdatingGroupUsers] = useState(false)

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    getApiRequestParamsByUrl,
    pageIndex,
    pageSize,
    sorting,
    search,
  } = useQueryParams()

  const totalPages = Math.ceil(rowCount / pageSize)

  const getUserListData = async () => {
    try {
      const apiParams = getApiRequestParamsByUrl()
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

  // closes the user assignment modal after update
  useEffect(() => {
    if (isUpdatingGroupUsers === false) {
      setIsAssignUsersOpen(false)
    }
  }, [isUpdatingGroupUsers])

  useEffect(() => {
    if (originalUsers.length > 0) {
      getUserListData()
    } else {
      setIsLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex, pageSize, sorting, search, originalUsers])

  const handleUpdateGroupUsers = async (userSelection: RowSelectionState) => {
    setIsLoading(true)
    setIsUpdatingGroupUsers(true)
    const selectedUserIds = Object.keys(userSelection).filter(key => userSelection[key])
    const selectedUserInfo = selectedUserIds.map(userId => ({ id: userId, assignedAt: new Date().toISOString() }))
    const updateUserData = selectedUserInfo.concat(originalUsers)
    try {
      await patchGroupUsers(groupData.id, updateUserData)
      router.refresh()
    } catch {
      console.error('An error occurred while updating group users')
    } finally {
      setIsLoading(false)
      setIsUpdatingGroupUsers(false)
    }
  }

  const CustomElement = (
    <Button onClick={() => setIsAssignUsersOpen(true)}>
      <Plus />
      {t('users.assign')}
    </Button>
  )

  if (!isLoading && users.length === 0 && originalUsers.length === 0) {
    return (
      <div className="h-full">
        <NoDataPage
          title={t('users.noUsers')}
          subTitle={t('users.noUsersSub')}
          buttonText={t('users.assign')}
          onButtonClick={() => setIsAssignUsersOpen(true)}
        />
        <AssignUsersModal
          isUpdating={isUpdatingGroupUsers}
          originalUsers={originalUsers}
          groupTitle={groupData.title}
          open={isAssignUsersOpen}
          onOpenChange={setIsAssignUsersOpen}
          onUpdateUsers={handleUpdateGroupUsers}
        />
      </div>
    )
  }

  return (
    <div className="h-full">
      <SearchHeader searchString={search} onChangeSearchString={setSearchParam} customElement={CustomElement} />
      <TableContainer>
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
      </TableContainer>
      <AssignUsersModal
        isUpdating={isUpdatingGroupUsers}
        originalUsers={originalUsers}
        groupTitle={groupData.title}
        open={isAssignUsersOpen}
        onOpenChange={setIsAssignUsersOpen}
        onUpdateUsers={handleUpdateGroupUsers}
      />
    </div>
  )
}
