'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { usePatchGroup } from '@/app/services/api/groups/clientRequests'
import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { Group, GroupTabProps } from '@/types/groups'
import { mapGroupListUsers } from '@/utils/users'

import { AssignUsersModal } from './AssignUsersModal'
import { UsersTable } from './UsersTable'

interface UsersTabProps extends GroupTabProps {
  groupData: Group
}
export const UsersTab = (props: UsersTabProps) => {
  const { groupData } = props
  const originalUsers = groupData.users
  const t = useTranslations('groups')
  const updateGroup = usePatchGroup()

  const [isAssignUsersOpen, setIsAssignUsersOpen] = useState(false)

  const {
    setSortingParams,
    setPaginationParams,
    setSearchParam,
    setTotalPages,
    getApiRequestParamsByUrl,
    pageIndex,
    pageSize,
    sorting,
    search,
    totalPages,
  } = useQueryParams()

  const userRequestParams = useMemo(() => {
    const params = new URLSearchParams(getApiRequestParamsByUrl())
    originalUsers.forEach(user => {
      params.append('id', String(user.id))
    })
    return params
  }, [getApiRequestParamsByUrl, originalUsers])

  const { data: usersData, isFetching: isFetchingUsers } = useGetUsers({
    params: userRequestParams,
    isEnabled: originalUsers.length > 0,
  })

  const isLoading = isFetchingUsers || updateGroup.isPending
  const rowCount = usersData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

  const users = useMemo(
    () => (isLoading ? [] : mapGroupListUsers(usersData?.data || [], originalUsers)),
    [usersData?.data, originalUsers, isLoading],
  )

  // closes the user assignment modal after update
  useEffect(() => {
    if (!updateGroup.isPending) {
      setIsAssignUsersOpen(false)
    }
  }, [updateGroup.isPending])

  const handleUpdateGroupUsers = async (userSelection: RowSelectionState) => {
    const selectedUserIds = Object.keys(userSelection).filter(key => userSelection[key])
    const selectedUserInfo = selectedUserIds.map(userId => ({ id: userId, assignedAt: new Date().toISOString() }))
    const updateUserData = selectedUserInfo.concat(originalUsers)
    updateGroup.mutate({ id: groupData.id, users: updateUserData })
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
          isUpdating={updateGroup.isPending}
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
        isUpdating={updateGroup.isPending}
        originalUsers={originalUsers}
        groupTitle={groupData.title}
        open={isAssignUsersOpen}
        onOpenChange={setIsAssignUsersOpen}
        onUpdateUsers={handleUpdateGroupUsers}
      />
    </div>
  )
}
