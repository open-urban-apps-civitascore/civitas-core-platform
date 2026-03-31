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
  isReadOnly: boolean
  onAssignGroups: (groupIds: string[]) => void
  onRemoveGroup: (id: string) => void
}

export const GroupsTab = (props: GroupsTabProps) => {
  const { userId, formValues, isReadOnly, onAssignGroups, onRemoveGroup } = props
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

  // Primary query: server-side filter by member
  const memberParams = useMemo(() => new URLSearchParams({ memberId: userId }), [userId])
  const {
    data: serverGroupsData,
    isFetching: isLoadingServerGroups,
    isSuccess: hasServerGroupsLoaded,
    error: serverGroupsError,
  } = useGetGroups({ isEnabled: !!userId, params: memberParams })

  // Derive original group IDs from server state
  const serverGroupIds = useMemo(() => serverGroupsData?.data.map(g => g.id) || [], [serverGroupsData])

  // Compute locally added group IDs (in form but not on server) — only after server data loaded
  const addedGroupIds = useMemo(
    () => (hasServerGroupsLoaded ? formValues.groupIds.filter(id => !serverGroupIds.includes(id)) : []),
    [formValues.groupIds, serverGroupIds, hasServerGroupsLoaded],
  )

  // Secondary query: fetch locally-added groups that aren't on the server yet
  const addedParams = useMemo(() => {
    const params = new URLSearchParams()
    addedGroupIds.forEach(id => params.append('id', id))
    return params
  }, [addedGroupIds])
  const { data: addedGroupsData, isFetching: isLoadingAddedGroups } = useGetGroups({
    isEnabled: addedGroupIds.length > 0,
    params: addedParams,
  })

  // Combine: server groups (minus removed) + added groups
  const groups = useMemo(() => {
    const serverGroups = (serverGroupsData?.data || []).filter(g => formValues.groupIds.includes(g.id))
    const addedGroups = (addedGroupsData?.data || []).filter(g => addedGroupIds.includes(g.id))
    return mapGroupsApiToListData([...serverGroups, ...addedGroups])
  }, [serverGroupsData, addedGroupsData, formValues.groupIds, addedGroupIds])

  const filteredGroups = useMemo(() => {
    if (searchString) {
      return groups.filter(
        group =>
          group.name.toLowerCase().includes(searchString.toLowerCase()) ||
          group.contactUser?.name.toLowerCase().includes(searchString.toLowerCase()) ||
          group.description.toLowerCase().includes(searchString.toLowerCase()),
      )
    } else {
      return groups
    }
  }, [groups, searchString])

  const rowCount = groups.length
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
                groups={filteredGroups}
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
