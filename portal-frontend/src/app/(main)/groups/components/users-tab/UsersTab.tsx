'use client'

import { RowSelectionState } from '@tanstack/react-table'
import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'
import { UseFormReturn } from 'react-hook-form'

import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { NoDataPage } from '@/components/no-data-page/NoDataPage'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { useQueryParams } from '@/hooks/use-query-params'
import { GroupBaseFormData } from '@/types/groups'
import { mapGroupListUsers } from '@/utils/users'

import { AssignUsersModal } from './AssignUsersModal'
import { UsersTable } from './UsersTable'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'

interface UsersTabProps {
  form: UseFormReturn<GroupBaseFormData>
  originalUsers: string[]
  isReadOnly: boolean
  isUpdatingGroup: boolean
}
export const UsersTab = (props: UsersTabProps) => {
  const { form, originalUsers, isReadOnly, isUpdatingGroup } = props
  const t = useTranslations('groups')
  const tCommon = useTranslations('common')
  const [userToRemove, setUserToRemove] = useState<string | null>(null)
  const [isAssignUsersOpen, setIsAssignUsersOpen] = useState(false)
  const [isRemoveUserWarningModalOpen, setIsRemoveUserWarningModalOpen] = useState(false)

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

  const usersWatch = form.watch('members')
  const nameWatch = form.watch('name')

  const shouldLoadUsers = usersWatch.length > 0
  const userRequestParams = useMemo(() => {
    const params = new URLSearchParams(getApiRequestParamsByUrl())
    usersWatch?.forEach(user => {
      params.append('id', user)
    })
    return params
  }, [getApiRequestParamsByUrl, usersWatch])

  const { data: usersData, isFetching: isFetchingUsers } = useGetUsers({
    params: userRequestParams,
    isEnabled: shouldLoadUsers,
  })

  const isLoading = isFetchingUsers || isUpdatingGroup
  const rowCount = usersData?.totalElements || 0

  useEffect(() => {
    setTotalPages(Math.ceil(rowCount / pageSize))
  }, [rowCount, setTotalPages, pageSize])

  const users = useMemo(
    () => mapGroupListUsers(shouldLoadUsers && usersData?.data ? usersData?.data : []),
    [usersData?.data, shouldLoadUsers],
  )

  const handleAssignUsers = (userSelection: RowSelectionState) => {
    const selectedUserIds = Object.keys(userSelection).filter(key => userSelection[key])
    const newUsers = Array.from(new Set([...usersWatch, ...selectedUserIds]))
    form.setValue('members', newUsers, { shouldDirty: true })
    setIsAssignUsersOpen(false)
  }

  const handleRemoveUser = () => {
    const newUsers = usersWatch.filter(user => user !== userToRemove)
    form.setValue('members', newUsers, { shouldDirty: true })
    setIsRemoveUserWarningModalOpen(false)
  }

  const handleRemoveUserClick = (userId: string) => {
    setUserToRemove(userId)
    setIsRemoveUserWarningModalOpen(true)
  }

  const CustomElement = (
    <Button type="button" onClick={() => setIsAssignUsersOpen(true)}>
      <Plus />
      {t('users.assign')}
    </Button>
  )

  return (
    <div className="h-full">
      {isLoading && users.length === 0 && <LoadingSpinner className="h-full" />}
      {!isLoading && users.length === 0 && originalUsers.length === 0 ? (
        <NoDataPage
          title={t('users.noUsers')}
          subTitle={t('users.noUsersSub')}
          buttonText={!isReadOnly ? t('users.assign') : undefined}
          onButtonClick={() => setIsAssignUsersOpen(true)}
        />
      ) : (
        <>
          <SearchHeader
            searchString={search}
            onChangeSearchString={setSearchParam}
            customElement={!isReadOnly ? CustomElement : undefined}
          />
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
              onRemoveUserClick={handleRemoveUserClick}
              isLoading={isLoading}
              isReadOnly={isReadOnly}
            />
            <WarningModal
              open={isRemoveUserWarningModalOpen}
              title={t('users.removeUserModal.title')}
              description={t('users.removeUserModal.description')}
              onConfirm={handleRemoveUser}
              onDiscard={() => setIsRemoveUserWarningModalOpen(false)}
              confirmButtonTitle={tCommon('actions.remove')}
            />
          </TableContainer>
        </>
      )}
      <AssignUsersModal
        isUpdating={isUpdatingGroup}
        assignedFormUsers={usersWatch}
        groupTitle={nameWatch}
        open={isAssignUsersOpen}
        onOpenChange={setIsAssignUsersOpen}
        onAssignUsers={handleAssignUsers}
      />
    </div>
  )
}
