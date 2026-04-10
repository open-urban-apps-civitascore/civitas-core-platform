import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useEffect, useMemo, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { UserFormData } from '@/types/users'
import { mapGroupsApiToListData } from '@/utils/groups'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import GroupsTable from './GroupsTable'

interface GroupsTabProps {
  formValues: UserFormData
  isReadOnly: boolean
  onAssignGroups: (groupIds: string[]) => void
  onRemoveGroup: (id: string) => void
}

export const GroupsTab = (props: GroupsTabProps) => {
  const { formValues, isReadOnly, onAssignGroups, onRemoveGroup } = props
  const t = useTranslations('users')
  const tCommon = useTranslations('common')
  const tGroups = useTranslations('groups')
  const [searchString, setSearchString] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sorting, setSorting] = useState<SortingState>([{ id: 'name', desc: false }])
  const [isGroupAssignmentModalOpen, setIsGroupAssignmentModalOpen] = useState(false)
  const [groupToRemove, setGroupToRemove] = useState<string | null>(null)
  const [isWarningModalOpen, setIsWarningModalOpen] = useState(false)

  // Send all group IDs + page/size/sort/search to server (same pattern as UsersTab in groups)
  const groupParams = useMemo(() => {
    const params = new URLSearchParams()
    formValues.groupIds.forEach(id => params.append('id', id))
    params.set('page', String(pageIndex))
    params.set('size', String(pageSize))
    if (sorting.length > 0) {
      params.set('sort', `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`)
    }
    if (searchString) {
      params.set('name', searchString)
    }
    return params
  }, [formValues.groupIds, pageIndex, pageSize, sorting, searchString])

  const {
    data: groupsData,
    isFetching: isLoadingGroups,
    error: groupsError,
  } = useGetGroups({ isEnabled: formValues.groupIds.length > 0, params: groupParams })

  const groups = useMemo(() => mapGroupsApiToListData(groupsData?.data || []), [groupsData])

  const rowCount = groupsData?.totalElements || 0
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  // Reset to first page when group list changes (add/remove)
  useEffect(() => {
    setPageIndex(0)
  }, [formValues.groupIds.length])

  const isLoading = isLoadingGroups
  const error = groupsError

  const handlePagination = (newPagination: PaginationState) => {
    setPageIndex(newPagination.pageIndex)
    setPageSize(newPagination.pageSize)
  }

  const handleRemoveGroupClick = (id: string) => {
    setGroupToRemove(id)
    setIsWarningModalOpen(true)
  }

  const handleWarningModalConfirm = () => {
    if (groupToRemove) onRemoveGroup(groupToRemove)
    setGroupToRemove(null)
    setIsWarningModalOpen(false)
  }

  const GroupsAssignmentButton = (
    <Button onClick={() => setIsGroupAssignmentModalOpen(true)}>{t('groupsTab.addGroup')}</Button>
  )
  if (isLoading) <LoadingSpinner className="h-full" />
  if (error) <p className="h-full flex items-center justify-center">{tCommon('errors.loadingError')}</p>
  return (
    <>
      <SearchHeader
        searchString={searchString}
        onChangeSearchString={setSearchString}
        customElement={!isReadOnly ? GroupsAssignmentButton : undefined}
      />
      <TableContainer shouldRespectSearchHeight>
        <GroupsTable
          groups={groups}
          rowCount={rowCount}
          pageIndex={pageIndex}
          pageSize={pageSize}
          onPaginationChange={handlePagination}
          sorting={sorting}
          onSortingChange={setSorting}
          totalPages={totalPages}
          isLoading={isLoading}
          onRemoveGroupClick={handleRemoveGroupClick}
          isReadOnly={isReadOnly}
        />
      </TableContainer>
      <GroupAssignmentModal
        open={isGroupAssignmentModalOpen}
        userName={`${formValues.firstName} ${formValues.lastName}`}
        assignedGroups={formValues.groupIds}
        onAssignGroups={onAssignGroups}
        onOpenChange={setIsGroupAssignmentModalOpen}
      />
      <WarningModal
        title={tGroups('users.removeUserModal.title')}
        description={tGroups('users.removeUserModal.description')}
        open={isWarningModalOpen}
        confirmButtonTitle={tCommon('actions.remove')}
        onOpenChange={setIsWarningModalOpen}
        onDiscard={() => setIsWarningModalOpen(false)}
        onConfirm={handleWarningModalConfirm}
      />
    </>
  )
}
