'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { apiRequest } from '@/app/services/api/request/apiRequest'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-field-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/useQueryParams'
import { Group, GroupTabProps } from '@/types/groups'
import { Authority, UserResponse } from '@/types/users'
import { mapGroupListUsers } from '@/utils/users'

import { AssignUsersModal } from './AssignUsersModal'
import UsersTable from './UsersTable'

interface UsersTabProps extends GroupTabProps {
  groupData: Group
}
export const UsersTab = (props: UsersTabProps) => {
  const { groupData } = props
  const originalUsers = groupData.users
  const t = useTranslations('groups')
  const queryClient = useQueryClient()
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

  const updateGroupUsersMutation = useMutation({
    mutationFn: (updateGroupData: typeof originalUsers) =>
      apiRequest<Group>({
        method: 'PATCH',
        endpoint: `/groups/${groupData.id}`,
        data: { users: updateGroupData },
        errorMessage: 'An error occurred while updating the group.',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['group', groupData.id] })
    },
    onError: error => {
      console.error(`An error occurred while updating group users. ${error}`)
    },
  })

  const userRequestParams = useMemo(() => {
    const params = new URLSearchParams(getApiRequestParamsByUrl())
    originalUsers.forEach(user => {
      params.append('id', String(user.id))
    })
    return params
  }, [getApiRequestParamsByUrl, originalUsers])

  const { data: usersData, isFetching: isFetchingUsers } = useQuery({
    queryKey: ['users', userRequestParams.toString()],
    queryFn: () => {
      return apiRequest<UserResponse[]>({
        endpoint: '/users',
        method: 'GET',
        params: userRequestParams,
        errorMessage: 'An error occurred while fetching users.',
      })
    },
    enabled: originalUsers.length > 0,
    placeholderData: previousData => previousData,
  })

  const { data: authoritiesData, isLoading: areAuthoritiesLoading } = useQuery({
    queryKey: ['authorities'],
    queryFn: () => {
      return apiRequest<Authority[]>({
        endpoint: '/authorities',
        method: 'GET',
        errorMessage: 'An error occurred while fetching authorities.',
      })
    },
    enabled: originalUsers.length > 0,
  })

  const isLoading = isFetchingUsers || areAuthoritiesLoading || updateGroupUsersMutation.isPending
  const rowCount = usersData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

  const users = useMemo(
    () => (isLoading ? [] : mapGroupListUsers(usersData?.data || [], authoritiesData?.data || [], originalUsers)),
    [usersData?.data, authoritiesData?.data, originalUsers, isLoading],
  )

  // closes the user assignment modal after update
  useEffect(() => {
    if (!updateGroupUsersMutation.isPending) {
      setIsAssignUsersOpen(false)
    }
  }, [updateGroupUsersMutation.isPending])

  const handleUpdateGroupUsers = async (userSelection: RowSelectionState) => {
    const selectedUserIds = Object.keys(userSelection).filter(key => userSelection[key])
    const selectedUserInfo = selectedUserIds.map(userId => ({ id: userId, assignedAt: new Date().toISOString() }))
    const updateUserData = selectedUserInfo.concat(originalUsers)
    updateGroupUsersMutation.mutate(updateUserData)
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
          isUpdating={updateGroupUsersMutation.isPending}
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
        isUpdating={updateGroupUsersMutation.isPending}
        originalUsers={originalUsers}
        groupTitle={groupData.title}
        open={isAssignUsersOpen}
        onOpenChange={setIsAssignUsersOpen}
        onUpdateUsers={handleUpdateGroupUsers}
      />
    </div>
  )
}
