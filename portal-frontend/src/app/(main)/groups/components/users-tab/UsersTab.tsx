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
import { Group } from '@/types/groups'
import { mapGroupListUsers } from '@/utils/users'

import { AssignUsersModal } from './AssignUsersModal'
import { UsersTable } from './UsersTable'

interface UsersTabProps {
  groupData: Group
}
export const UsersTab = (props: UsersTabProps) => {
  const { groupData } = props
  const originalUsers = groupData.members || []
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

  const getUserRequestParams = () => {
    const params = new URLSearchParams(getApiRequestParamsByUrl())
    originalUsers?.forEach(user => {
      params.append('id', user.id)
    })
    return params
  }


  const { data: usersData, isFetching: isFetchingUsers } = useGetUsers({
    queryKey: 'groupUsers',
    params: getUserRequestParams(),
    isEnabled: originalUsers?.length > 0,
  })


  const isLoading = isFetchingUsers || updateGroup.isPending
  const rowCount = usersData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

  const users = useMemo(() => (isLoading ? [] : mapGroupListUsers(usersData?.data || [])), [usersData?.data, isLoading])

  // closes the user assignment modal after update
  useEffect(() => {
    if (!updateGroup.isPending) {
      setIsAssignUsersOpen(false)
    }
  }, [updateGroup.isPending])

  const handleUpdateGroupUsers = async (userSelection: RowSelectionState) => {
    const selectedUserIds = Object.keys(userSelection).filter(key => userSelection[key])
    const updateUserData = selectedUserIds.concat(originalUsers.map(user => user.id))
    updateGroup.mutate({ id: groupData.id, memberIds: updateUserData })
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
          groupTitle={groupData.name}
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
        groupTitle={groupData.name}
        open={isAssignUsersOpen}
        onOpenChange={setIsAssignUsersOpen}
        onUpdateUsers={handleUpdateGroupUsers}
      />
    </div>
  )
}
