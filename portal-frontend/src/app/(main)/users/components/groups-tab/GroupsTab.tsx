import { PaginationState, SortingState } from '@tanstack/react-table'
import { useTranslations } from 'next-intl'
import { useMemo, useState } from 'react'

import { useGetGroups } from '@/app/services/api/groups/clientRequests'
import { ContentCard } from '@/components/content-card/ContentCard'
import { LoadingSpinner } from '@/components/loading-spinner/LoadingSpinner'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { PageBackground } from '@/components/page-background/PageBackground'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SearchHeader } from '@/components/search-area/SearchArea'
import { TableContainer } from '@/components/table-container/TableContainer'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { UserFormData } from '@/types/users'
import { mapGroupsApiToListData } from '@/utils/groups'

import { GroupAssignmentModal } from './GroupAssignmentModal'
import GroupsTable from './GroupsTable'

interface GroupsTabProps {
  userId: string
  formValues: UserFormData
  originalGroupIds: string[]
  isReadOnly: boolean
  onAssignGroups: (groupIds: string[]) => void
  onRemoveGroup: (id: string) => void
}

export const GroupsTab = (props: GroupsTabProps) => {
  const { userId, formValues, originalGroupIds, isReadOnly, onAssignGroups, onRemoveGroup } = props
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

  // Build server-side pagination/sort/filter params
  const memberParams = useMemo(() => {
    const params = new URLSearchParams({ memberIds: userId })
    params.set('page', String(pageIndex))
    params.set('size', String(pageSize))
    if (sorting.length > 0) {
      params.set('sort', `${sorting[0].id},${sorting[0].desc ? 'desc' : 'asc'}`)
    }
    if (searchString) {
      params.set('name', searchString)
    }
    return params
  }, [userId, pageIndex, pageSize, sorting, searchString])

  const {
    data: serverGroupsData,
    isFetching: isLoadingServerGroups,
    error: serverGroupsError,
  } = useGetGroups({ isEnabled: !!userId, params: memberParams })

  // Locally added groups: in form but not in the original saved state
  const addedGroupIds = useMemo(
    () => formValues.groupIds.filter(id => !originalGroupIds.includes(id)),
    [formValues.groupIds, originalGroupIds],
  )

  // Locally removed groups: in original saved state but removed from form
  const removedGroupIds = useMemo(
    () => originalGroupIds.filter(id => !formValues.groupIds.includes(id)),
    [originalGroupIds, formValues.groupIds],
  )

  // Fetch locally-added groups that aren't on the server yet
  const addedParams = useMemo(() => {
    const params = new URLSearchParams()
    addedGroupIds.forEach(id => params.append('id', id))
    return params
  }, [addedGroupIds])
  const { data: addedGroupsData, isFetching: isLoadingAddedGroups } = useGetGroups({
    isEnabled: addedGroupIds.length > 0,
    params: addedParams,
  })

  // Combine: server page (minus locally removed) + locally added groups on top
  const groups = useMemo(() => {
    const serverGroups = (serverGroupsData?.data || []).filter(g => !removedGroupIds.includes(g.id))
    const addedGroups = (addedGroupsData?.data || []).filter(g => addedGroupIds.includes(g.id))
    return mapGroupsApiToListData([...addedGroups, ...serverGroups])
  }, [serverGroupsData, addedGroupsData, addedGroupIds, removedGroupIds])

  const serverTotalElements = serverGroupsData?.totalElements || 0
  const rowCount = serverTotalElements - removedGroupIds.length + addedGroupIds.length
  const totalPages = Math.ceil(rowCount / pageSize) || 1

  const isLoading = isLoadingServerGroups || isLoadingAddedGroups
  const error = serverGroupsError

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
  return (
    <PageBackground hasBackground={!isReadOnly}>
      <ContentCard className={cn(!error && !isLoading ? 'h-full' : 'h-50')}>
        {!error && !isLoading && (
          <>
            <SubHeader title={t('groupsTab.title')} customElement={!isReadOnly ? GroupsAssignmentButton : undefined} />
            <SearchHeader searchString={searchString} onChangeSearchString={setSearchString} className="my-2" />
            <TableContainer className="[--search-height:calc(--spacing(30))]">
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
          </>
        )}
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
        {isLoading && <LoadingSpinner className="h-full" />}
        {error && <p className="h-full flex items-center justify-center">{tCommon('errors.loadingError')}</p>}
      </ContentCard>
    </PageBackground>
  )
}
